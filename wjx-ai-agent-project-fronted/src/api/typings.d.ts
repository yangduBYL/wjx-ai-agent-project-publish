declare namespace API {
  type App = {
    id?: number
    appName?: string
    cover?: string
    initprompt?: string
    codeGenType?: string
    deployKey?: string
    deployedTime?: string
    priority?: number
    userId?: number
    editTime?: string
    createTime?: string
    updateTime?: string
    isDelete?: number
  }

  type AppAddRequest = {
    appName?: string
    cover?: string
    initprompt?: string
    codeGenType?: string
    deployKey?: string
    deployedTime?: string
    priority?: number
    userId?: number
  }

  type AppDeployRequest = {
    appId?: number
  }

  type AppQueryRequest = {
    pageNum?: number
    pageSize?: number
    sortField?: string
    sortOrder?: string
    id?: number
    appName?: string
    cover?: string
    initprompt?: string
    codeGenType?: string
    deployKey?: string
    deployedTime?: string
    priority?: number
    userId?: number
    createTime?: string
  }

  type AppUpdateRequest = {
    id?: number
    appName?: string
    cover?: string
    initprompt?: string
    codeGenType?: string
    deployKey?: string
    deployedTime?: string
    priority?: number
    userId?: number
  }

  type AppUserVo = {
    id?: number
    appName?: string
    cover?: string
    initprompt?: string
    codeGenType?: string
    deployKey?: string
    deployedTime?: string
    priority?: number
    userId?: number
    createTime?: string
    uservo?: UserVo
  }

  type AppVo = {
    id?: number
    appName?: string
    cover?: string
    initprompt?: string
    codeGenType?: string
    deployKey?: string
    deployedTime?: string
    priority?: number
    userId?: number
    createTime?: string
  }

  type BaseResponseBoolean = {
    code?: number
    data?: boolean
    message?: string
  }

  type BaseResponseLong = {
    code?: number
    data?: number
    message?: string
  }

  type BaseResponsePageAppUserVo = {
    code?: number
    data?: PageAppUserVo
    message?: string
  }

  type BaseResponsePageAppVo = {
    code?: number
    data?: PageAppVo
    message?: string
  }

  type BaseResponsePageChatHistory = {
    code?: number
    data?: PageChatHistory
    message?: string
  }

  type BaseResponsePageUserVo = {
    code?: number
    data?: PageUserVo
    message?: string
  }

  type BaseResponseString = {
    code?: number
    data?: string
    message?: string
  }

  type BaseResponseUser = {
    code?: number
    data?: User
    message?: string
  }

  type BaseResponseUserLoginRequestVo = {
    code?: number
    data?: userLoginRequestVo
    message?: string
  }

  type BaseResponseUserVo = {
    code?: number
    data?: UserVo
    message?: string
  }

  type ChatHistory = {
    id?: number
    message?: string
    messageType?: string
    appId?: number
    userId?: number
    createTime?: string
    updateTime?: string
    isDelete?: number
    parentId?: number
  }

  type ChatHistoryAddRequest = {
    message?: string
    messageType?: string
    appId?: number
    userId?: number
    lastCreateTime?: string
  }

  type ChatHistoryQueryRequest = {
    pageNum?: number
    pageSize?: number
    sortField?: string
    sortOrder?: string
    id?: number
    message?: string
    messageType?: string
    appId?: number
    userId?: number
    lastCreateTime?: string
  }

  type ChatHistoryUpdateRequest = {
    id?: number
    message?: string
    messageType?: string
    appId?: number
    userId?: number
    lastCreateTime?: string
  }

  type chatToGenCodeParams = {
    appId: number
    message: string
  }

  type DeleteRequest = {
    id?: number
  }

  type getappByIdParams = {
    id: number
  }

  type getInfoParams = {
    id: number
  }

  type getUserByIdParams = {
    id: number
  }

  type PageAppUserVo = {
    records?: AppUserVo[]
    pageNumber?: number
    pageSize?: number
    totalPage?: number
    totalRow?: number
    optimizeCountQuery?: boolean
  }

  type PageAppVo = {
    records?: AppVo[]
    pageNumber?: number
    pageSize?: number
    totalPage?: number
    totalRow?: number
    optimizeCountQuery?: boolean
  }

  type PageChatHistory = {
    records?: ChatHistory[]
    pageNumber?: number
    pageSize?: number
    totalPage?: number
    totalRow?: number
    optimizeCountQuery?: boolean
  }

  type pageChathistoryParams = {
    appId: number
    lastCreateTime?: string
    pageSize?: number
  }

  type PageUserVo = {
    records?: UserVo[]
    pageNumber?: number
    pageSize?: number
    totalPage?: number
    totalRow?: number
    optimizeCountQuery?: boolean
  }

  type refreshCoverParams = {
    appId: number
  }

  type remove1Params = {
    id: number
  }

  type ServerSentEventString = true

  type serveStaticResourceParams = {
    deployKey: string
  }

  type User = {
    id?: number
    userAccount?: string
    userPassword?: string
    userName?: string
    userAvatar?: string
    userProfile?: string
    userRole?: string
    editTime?: string
    createTime?: string
    updateTime?: string
    isDelete?: number
    vipExpireTime?: string
    vipCode?: string
    vipNumber?: number
    userAchievement?: string
    shareCode?: string
    inviteUser?: number
  }

  type UserAddRequest = {
    userName?: string
    userAccount?: string
    userAvatar?: string
    userRole?: string
    userProfile?: string
  }

  type UserLoginRequest = {
    userAccount?: string
    userPassword?: string
  }

  type userLoginRequestVo = {
    id?: number
    userAccount?: string
    userName?: string
    userAvatar?: string
    userRole?: string
    userProfile?: string
    createTime?: string
    updateTime?: string
  }

  type UserQueryRequest = {
    pageNum?: number
    pageSize?: number
    sortField?: string
    sortOrder?: string
    id?: number
    userName?: string
    userAccount?: string
    userRole?: string
    userProfile?: string
  }

  type UserRegisterRequest = {
    userAccount?: string
    userPassword?: string
    checkPassword?: string
  }

  type UserUpdateRequest = {
    id?: number
    userName?: string
    userAccount?: string
    userAvatar?: string
    userRole?: string
    userProfile?: string
  }

  type UserVo = {
    id?: number
    userName?: string
    userAccount?: string
    userAvatar?: string
    userRole?: string
    userProfile?: string
    createTime?: string
  }
}
