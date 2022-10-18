package com.zs3.helper

import org.w3c.dom.Document
import org.w3c.dom.Node
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths.get
import java.nio.file.StandardCopyOption
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import javax.xml.xpath.XPathConstants
import javax.xml.xpath.XPathFactory
import kotlin.io.path.isDirectory

private const val EXAM_TYPE = "ExamType"
private const val IMAGE_PRESET_TYPE = "ImagePresetType"

object ZS3BackupHelper {

    fun removeAnnotationInDir(path: String) {
        forEachBackup(path) { path ->
            removeAnnotation(path)
        }
    }

    private fun removeAnnotation(path: Path) {
        doOnFile(path, "app.xml") { xml ->
            parseXmlAndRemoveAnnotations(xml)
        }
    }

    private fun parseXmlAndRemoveAnnotations(path: Path) {
        val document = readXml(path)
        val xpFactory = XPathFactory.newInstance()
        val xPath = xpFactory.newXPath()
        val xpath = "ImgApplication/Comment_Group/Comment_Text_Group/Comment_Text_Count"
        // find node of comment_text_count using evaluate()
        val commentCount: Node = xPath.evaluate(xpath, document, XPathConstants.NODE) as Node
        // record the value for number of comments
        val numberOfComments = commentCount.attributes.getNamedItem("value").nodeValue?.toInt() ?: -1
        // set the value of comment_text_count in the xml file to 0
        commentCount.attributes.getNamedItem("value").nodeValue = "0"
        when (numberOfComments) {
            -1 -> println("error")
            0 -> println("no comment")
            else -> removeCommentNodes(document, numberOfComments, path) //if comment exist, remove it
        }
    }

    private fun removeCommentNodes(document: Document, count: Int, path: Path) {
        for (i in 0 until count) { //Comment_Text_Element starts from 0
            val node = document.getElementsByTagName("Comment_Text_Element$i").item(0)
            node.parentNode.removeChild(node) //remove each comment nodes
        }
        println("Comment nodes removed")

        val backupFile = "app.backup.xml"
        Files.copy(path, path.parent.resolve(backupFile)) //save a backup version in case need to revert
        println("Backup saved")

        val transformerFactory = TransformerFactory.newInstance()
        val transformer = transformerFactory.newTransformer()
        val dSource = DOMSource(document)
        val result = StreamResult(File(path.toString()))
        transformer.transform(dSource, result) //update the modified xml file
        println("file updated")
    }

    private fun readXml(path: Path): Document {
        val dbFactory = DocumentBuilderFactory.newInstance()
        val dBuilder = dbFactory.newDocumentBuilder()
        return dBuilder.parse(path.toFile())
    }

    fun categorizeBackups(path: String, newPathString: String?) {
        if (newPathString?.isNotBlank() != true) {
            println("target path is missing: $newPathString")
            return
        }
        val newPath = get(newPathString)
        if (Files.exists(newPath) && (!Files.isDirectory(newPath) || !isEmptyDirectory(newPath))) {
            println("target path is not empty: $newPathString")
            return
        }
        // record path of the file and combinations of exam type and preset type
        val categoryMap = HashMap<Pair<String, String>, ArrayList<Path>>()
        forEachBackup(path) { dir ->
            doOnFile(dir, "BackEndSystemInfo.txt") { file ->
                val nullablePair = getSystemPresetTypes(file)
                if (nullablePair == null) {
                    println("No system info found: $file")
                } else {
                    val fileList: ArrayList<Path> = categoryMap[nullablePair] ?: ArrayList()
                    fileList.add(file.parent)
                    categoryMap[nullablePair] = fileList
                }
            }
        }
        moveBackups(categoryMap, newPath)
    }

    private fun moveBackups(map: HashMap<Pair<String, String>, ArrayList<Path>>, newPath: Path) {
        map.forEach { (key, list) ->
            println("create new folder $key")
            val categoryFolder = newPath.resolve(key.first).resolve(key.second)
            val newDirectory = File(categoryFolder.toString())
            newDirectory.mkdirs() //create folder for each category

            list.forEach {path ->
                println("move $path to new folder $key")
                val backupName = File(path.toString()).name
                val subdirectoryPath = categoryFolder.resolve(backupName)
                val newSubdirectory = File(subdirectoryPath.toString())
                newSubdirectory.mkdir() //create folder for each backup
                Files.move(path, subdirectoryPath, StandardCopyOption.REPLACE_EXISTING) //move the files of each backup
                val frmName = "$backupName.FRM"
                val frmPath = getFilePath(path, frmName)
                //move the corresponding FRM file of each backup
                Files.move(frmPath, getFilePath(subdirectoryPath, frmName), StandardCopyOption.REPLACE_EXISTING)
                println("$path moved successfully")
            }
        }
    }


    private fun getSystemPresetTypes(path: Path): Pair<String, String>? {
        //pair the Exam Type and Preset Type for categorization
        var pair: Pair<String, String>? = null
        try {
            Files.lines(path).use { stream ->
                val map: HashMap<String, String> = stream
                    .reduce(HashMap(),
                        { t: HashMap<String, String>, u: String ->
                            if (u.contains(EXAM_TYPE)) {
                                t[EXAM_TYPE] = u.substringAfter("ExamType=")
                            } else if (u.contains(IMAGE_PRESET_TYPE)) {
                                t[IMAGE_PRESET_TYPE] = u.substringAfter("ImagePresetType=")
                            }
                            t
                        },
                        { t: java.util.HashMap<String, String>, u: java.util.HashMap<String, String> ->
                            t.putAll(u)
                            t
                        }
                    )
                pair = map[EXAM_TYPE]?.let { examType ->
                    map[IMAGE_PRESET_TYPE]?.let { preset ->
                        Pair(examType, preset)
                    }
                }
            }
        } catch (e: IOException) {
            println(e)
        }
        return pair
    }

    private fun forEachBackup(path: String, action: (Path) -> Unit) {
        val currentPath = get(path)
        if (!Files.exists(currentPath) || !currentPath.isDirectory()) {
            println("Invalid path $path")
            return
        }
        Files.walk(currentPath, 1)
            .filter { element: Path -> Files.isDirectory(element) && element != currentPath }
            .forEach { action.invoke(it) }
    }

    private fun getFilePath(path: Path, fileName: String): Path {
        return path.parent.resolve(fileName) //return path of the file itself
    }

    private fun doOnFile(dir: Path, name: String, action: (Path) -> Unit) {
        Files.list(dir)
            .filter { file ->
                file.fileName.endsWith(name)
            }
            .forEach { xml ->
                action.invoke(xml)
            }
    }

    fun validatePathParameter(args: Array<String>, action: (String, Array<String>) -> Unit) {
        val targetDir = args.runCatching {
            get(1)
        }.getOrNull()
        if (targetDir?.isNotBlank() == true) {
            action.invoke(targetDir, args)
        } else {
            println("Input path is invalid: $targetDir")
        }
    }

    private fun isEmptyDirectory(path: Path): Boolean = !Files.list(path).findAny().isPresent
}