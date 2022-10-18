import com.zs3.helper.ZS3BackupHelper

private const val REMOVE_ANNOTATION_TAG = "ra"
private const val CATEGORY_TAG = "ca"

fun main(args: Array<String>) { //give program arguments at configurations
    when (args.first()) { //Use the tag for the function need to use
        REMOVE_ANNOTATION_TAG -> ZS3BackupHelper.validatePathParameter(args) { path, args ->
            ZS3BackupHelper.removeAnnotationInDir(path)
        }

        CATEGORY_TAG -> ZS3BackupHelper.validatePathParameter(args) { path, args ->
            val newPath = args.runCatching { //path of where the output file locate
                get(2)
            }.getOrDefault(null)
            ZS3BackupHelper.categorizeBackups(path, newPath)
        }

        else -> println("Unknown action.")
    }
}