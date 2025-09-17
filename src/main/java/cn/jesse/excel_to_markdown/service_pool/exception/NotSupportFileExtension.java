package cn.jesse.excel_to_markdown.service_pool.exception;

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
