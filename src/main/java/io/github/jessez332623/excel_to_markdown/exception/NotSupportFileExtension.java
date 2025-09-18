package io.github.jessez332623.excel_to_markdown.exception;

/** 检查到不支持的文件扩展名时抛出本异常。*/
public class NotSupportFileExtension
    extends IllegalArgumentException
{
    public NotSupportFileExtension(String message) {
        super(message);
    }

    public NotSupportFileExtension(String message, Throwable cause) {
        super(message, cause);
    }
}
