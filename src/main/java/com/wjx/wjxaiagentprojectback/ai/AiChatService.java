package com.wjx.wjxaiagentprojectback.ai;

import com.wjx.wjxaiagentprojectback.ai.model.FileCodeResult;
import com.wjx.wjxaiagentprojectback.ai.model.HtmlCodeResult;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import reactor.core.publisher.Flux;

public interface AiChatService {
    //系统提示词
    @SystemMessage("You are a good friend of mine. Answer using slang.")
    String chat(String userMessage);
    //
    @SystemMessage(fromResource = "prompt/OneWebPage.txt")
    String webchat(String userMessage);

    @SystemMessage(fromResource = "prompt/OneWebPage.txt")
    HtmlCodeResult htmlchat(@MemoryId int memoryId,@UserMessage String userMessage);

    @SystemMessage(fromResource = "prompt/twoWebPages.txt")
    FileCodeResult filechat(@MemoryId int memoryId,@UserMessage String userMessage);

    /**
     * 生成 HTML 代码（流式）
     *
     * @param userMessage 用户消息
     * @return 生成的代码结果
     */
    @SystemMessage(fromResource = "prompt/OneWebPage.txt")
    Flux<String> generateHtmlCodeStream(@MemoryId int memoryId,@UserMessage String userMessage);

    /**
     * 生成多文件代码（流式）
     *
     * @param userMessage 用户消息
     * @return 生成的代码结果
     */
    @SystemMessage(fromResource = "prompt/twoWebPages.txt")
    Flux<String> generateMultiFileCodeStream(@MemoryId int memoryId,@UserMessage String userMessage);

}
