package cn.jesse.excel_to_markdown.service_pool.utils;

import cn.jesse.excel_to_markdown.service_pool.exception.NotSupportFileExtension;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;
import java.util.Set;

/** 文件扩展名检查器。*/
public class FileExtensionChecker
{
    /** 支持进行转换的文件扩展名集合。*/
    public static final Set<String>
    SUPPORT_FILE_EXTENSION = Set.of(".xlsx", ".xlsm", ".xlsb", ".xls", ".csv");

    /** 检查某个字符串是否为空串。*/
    public static boolean
    isNotEmptyString(String string) {
        return (string != null) && !(string.trim().isEmpty());
    }

    /** 提取文件扩展名。*/
    public static @NotNull String
    extractFileExtension(@NotNull String fileName)
    {
        int dotIndex = fileName.lastIndexOf('.');

        if (dotIndex == -1)
        {
            throw new
            NotSupportFileExtension(
                String.format("File %s don't have extension!", fileName)
            );
        }

        return
        fileName.substring(dotIndex)
                .toLowerCase(Locale.ROOT);
    }

    /** 执行检查 */
    public static void
    check(String fileName)
    {
        if(!isNotEmptyString(fileName))
        {
            throw new
            NotSupportFileExtension("File name is null or empty!");
        }

        final String extension
            = extractFileExtension(fileName);

        if (!SUPPORT_FILE_EXTENSION.contains(extension))
        {
            throw new
            NotSupportFileExtension(
                String.format(
                    "File extension: %s not support! Only support type of %s extensions!",
                    extension, SUPPORT_FILE_EXTENSION
                )
            );
        }
    }
}