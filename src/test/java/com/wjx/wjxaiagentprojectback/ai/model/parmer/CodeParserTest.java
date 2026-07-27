package com.wjx.wjxaiagentprojectback.ai.model.parmer;

import com.wjx.wjxaiagentprojectback.ai.model.FileCodeResult;
import com.wjx.wjxaiagentprojectback.ai.model.HtmlCodeResult;
import com.wjx.wjxaiagentprojectback.ai.model.parser.HtmlCodeParser;
import com.wjx.wjxaiagentprojectback.ai.model.parser.MultiFileCodeParser;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
class CodeParserTest {
    @Test
    void parseHtmlCode() {
        String codeContent = """
                随便写一段描述：
                html 格式
                <!DOCTYPE html>
                <html>
                <head>
                    <title>测试页面</title>
                </head>
                <body>
                    <h1>Hello World!</h1>
                </body>
                </html>

                随便写一段描述
                """;
        HtmlCodeParser parser = new HtmlCodeParser();
        HtmlCodeResult result = parser.parseCode(codeContent);
        assertNotNull(result);
        assertNotNull(result.getHtmlCode());
    }


    @Test
    void parseMultiFileCode() {
        String codeContent = """
            创建一个完整的网页：
            ```html
            <!DOCTYPE html>
            <html>
            <head>
                <title>多文件示例</title>
                <link rel="stylesheet" href="style.css">
            </head>
            <body>
                <h1>欢迎使用</h1>
                <script src="script.js"></script>
            </body>
            </html>
            ```
            ```css
            h1 {
                color: blue;
                text-align: center;
            }
            ```
            ```js
            console.log('页面加载完成');
            ```
            文件创建完成！
            """;
        MultiFileCodeParser parser = new MultiFileCodeParser();
        FileCodeResult result = parser.parseCode(codeContent);
        assertNotNull(result);
        assertNotNull(result.getHtmlCode());
        assertNotNull(result.getCssCode());
        assertNotNull(result.getJsCode());
    }


}