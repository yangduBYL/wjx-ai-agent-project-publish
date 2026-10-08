package com.wjx.wjxaiagentprojectback.ai;

import com.wjx.wjxaiagentprojectback.ai.model.FileCodeResult;
import com.wjx.wjxaiagentprojectback.ai.model.HtmlCodeResult;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;
import reactor.core.publisher.Flux;

public interface AiChatService {
    //系统提示词
    @SystemMessage("You are a good friend of mine. Answer using slang.")
    String chat(String userMessage);
    //
    @SystemMessage(fromResource = "prompt/OneWebPage.txt")
    String webchat(String userMessage);


    /**
     * memoryId的作用：用来关联对话历史信息的。
     * @param memoryId
     * @param userMessage
     * @return
     */
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
    //                                          int memoryId 关联并隔离对话历史
    Flux<String> generateMultiFileCodeStream(@MemoryId int memoryId,@UserMessage String userMessage);

    /**
     * 生成 Vue 项目代码（流式）
     *根据langchain4j的特性，用注解@MemoryId来附带传值appId,以此来生成独一无二的Vue项目文件（防止文件重名）。
     * @param userMessage 用户消息
     * @return 生成过程的流式响应
     */
    @SystemMessage(fromResource = "prompt/VueTextProject.txt")
    TokenStream generateVueProjectCodeStream(@MemoryId long appId, @UserMessage String userMessage);


}
