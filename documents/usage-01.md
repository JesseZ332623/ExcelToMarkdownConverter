# 测试用例-01 读取文件上传转换成 Markdown 后再上传

## 属性配置

```properties
app.excel-to-markdown.enabled=true
app.excel-to-markdown.processCount=8
app.excel-to-markdown.destroy.max-wait-seconds=10
app.excel-to-markdown.destroy.wait-interval-millis=500
```

## 测试用例

```java
@Slf4j
@Service
public class SysFileServiceImpl implements ISysFileService 
{
    /* ... */
    
    @Resource
    private OssTemplate ossTemplate;

    @Autowired
    private ConvertServicePoolManager scriptServicePoolManager;
    
    @Override
    public UploadResult
    uploadFile(MultipartFile file, String purpose) throws UploadException
    {
        // 获取客户端传来文件的原始文件名（比如：document.pdf test.txt）
        String fileName = Objects.requireNonNull(file.getOriginalFilename());

        /* 文件会被上传到远程存储服务器，这里保存文件在存储服务器的 URL */
        String fileUrl;

        /* 对于指定格式的文件，在本地会生成一个临时文件，交给其他进程进行相关处理。*/
        File temp = null;

        /* 对于表格附件，解析后的 Markdown 内容存于此处。*/
        String markdownContent = null;

        InputStream inputStream = null;

        // 获取文件输入流
        try {
            inputStream = file.getInputStream();

            // 若形参中没有指定文件存储目录，默认放在 default 存储目录下
            if (StrUtil.isEmpty(purpose)) {
                purpose = PurposeEnum.DEFAULT.getCode();
            }

            String suffix = ContentType.JPG.getSuffix();

            // 对于存在文件扩展名的文件，提取它
            if (StrUtil.isNotBlank(fileName) && fileName.lastIndexOf(StringPool.DOT) > -1) {
                suffix = fileName.substring(
                    fileName.lastIndexOf(StringPool.DOT)
                );
            }

            /*
             * 对于表格附件，
             * 需要在本地创建临时文件，解析成 Markdown 格式再返回给前端。
             */
            boolean isTableAttachment
                = PurposeEnum.ANNEX.getCode().equals(purpose) &&
                ATTACHMENT_SUFFIX_MAP.get("表格").contains(suffix);

            if (isTableAttachment) 
            {
                /* 临时文件格式：timestamp.xlsx */
                temp = File.createTempFile(
                    String.valueOf(System.currentTimeMillis()), suffix
                );

                // 创建临时文件
                file.transferTo(temp);

                // 转换成 Markdown 格式字符串
                markdownContent
                    = this.scriptServicePoolManager
                          .convertTableToMarkdown(temp.getAbsolutePath());
            }

            /*
             * 组装文件路径，格式：
             * 存储目录/年月/时间戳.扩展名
             *
             * 示例：project/avatar/202412/1756292250.png
             */
            fileName = MAIN_DIRECTORY           + "/" +
                       purpose                  + "/" +
                YearMonthSdf.format(new Date()) + "/" +
                System.currentTimeMillis()      + suffix;

            // 上传至远程服务器
            ossTemplate.putObject(fileName, inputStream);

            // 组装文件在远程服务器的 URL
            fileUrl = preFileUrl + StringPool.SLASH + fileName;

            // 组装文件上传结果
            return UploadResult.builder()
                .fileUrl(fileUrl)
                .markdownContent(markdownContent)
                .build();

        } catch (IOException e) {
            log.error("{}", e.getMessage(), e);

            throw new
                UploadException("文件上传失败！");
        } catch (ScriptWorkerException e) {
            log.error("{}", e.getMessage(), e);

            throw new
                UploadException(e.getMessage());
        } finally {
            try {
                if (inputStream != null) {
                    inputStream.close();
                }
            } catch (IOException e) {
                log.error("关闭文件流失败！原因：{}", e.getMessage(), e);
            }

            if (temp != null) {
                FileUtil.del(temp);
            }
        }

        log.info(
            "文件上传完成，耗时 {} ms。",
            System.currentTimeMillis() - start
        );
    }

    /* ... */
}
```
