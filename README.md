# 一个基于 markitdown 开源库的高性能 Excel 表转 Markdown 服务池

## 简介

在工作中遇到了需要处理 Excel 表转换成 Markdown 文本的业务，
最先我是简单的调用 [markitdown](https://github.com/microsoft/markitdown) 开源库，后来很快遇到了性能瓶颈，
后边我将转换操作服务化 + 池化，应用运行时多个转换服务常驻于后台，在解决性能瓶颈的同时大幅提升吞吐量，
如果项目中 Excel 表转 Markdown 是核心业务，可以考虑试试这个中间件。🚀🚀🚀

### 用法

在安装好依赖后，可以项目的配置文件中（如 `application.properties` 或 `application.yml`）中启用本服务池，
示例如下：

```properties
# 启动本服务
app.excel-to-markdown.enabled=true

# 后台常驻 8 个 Python 服务进程处理转换操作
app.excel-to-markdown.processCount=8
```

### 代码速览

[Excel 表格 -> Markdown Python 服务脚本](https://github.com/JesseZ332623/ExcelToMarkdownConverter/blob/main/src/main/resources/py-scripts/table_converter_service.py)

[Excel 表格 -> Markdown Python 服务池管理器默认实现](https://github.com/JesseZ332623/ExcelToMarkdownConverter/blob/main/src/main/java/cn/jesse/excel_to_markdown/service_pool/impl/DefaultConvertServicePoolManager.java)

[缓存服务脚本创建器](https://github.com/JesseZ332623/ExcelToMarkdownConverter/blob/main/src/main/java/cn/jesse/excel_to_markdown/service_pool/utils/CachedScriptCreator.java)

### LICENCE

[Apache License Version 2.0](https://github.com/JesseZ332623/ExcelToMarkdownConverter/blob/main/LICENSE)

### Latest Update

*2025.09.17*