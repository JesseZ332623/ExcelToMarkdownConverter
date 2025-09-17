# 将 EXCEL 表格文件解析成 Markdown 格式后输出的 Python 服务
# 在应用程序启动时一并启动本服务，相比原方案频繁的启动 / 销毁线程
# 服务化策略可以让一次附件上传的响应时间从原来的 2 - 3 秒提升至 250 毫秒内

import sys
import io
import warnings
import traceback

from markitdown import MarkItDown

# 设置字符集为 UTF-8
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
sys.stderr = io.TextIOWrapper(sys.stderr.buffer, encoding='utf-8')

# 抑制警告
warnings.filterwarnings('ignore', category=UserWarning, module='openpyxl')

END_MARK    = "@@END_OF_CONVERSION@@"
ERROR_MARK  = "@@END_OF_CONVERSION_ERROR@@"

# 初始化转换器（只一次）
converter = MarkItDown()

def convert_table_file(table_path):
    try:
        result = converter.convert(table_path)
        print(result.text_content)
        print(END_MARK)  # 结束标记
        sys.stdout.flush()

    except Exception as exception:

        # 输出详细的错误信息到标准错误
        error_msg = f"ERROR: {str(exception)}\n{traceback.format_exc()}"
        print(error_msg, file=sys.stderr)
        sys.stderr.flush()

        # 同时也在标准输出发送错误和结束标记
        print(ERROR_MARK)
        print(END_MARK)
        sys.stdout.flush()

if __name__ == "__main__":

    # 持续读取标准输入
    while True:
        try:
            # readline() 阻塞脚本，等待外部的输入
            line = sys.stdin.readline().strip()
            if not line:
                break  # 输入结束，退出循环
            if line == "exit":
                sys.stdout.flush()
                sys.stderr.flush()
                sys.exit(0)  # 退出命令
            convert_table_file(line)
            
        except Exception as e:
            # 捕获所有异常，防止进程崩溃
            print(f"FATAL: Inner exception from process: {str(e)}", file=sys.stderr)
            sys.stderr.flush()