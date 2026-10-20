// @ts-ignore
/* eslint-disable */
import request from '@/request'

/** 此处后端没有提供注释 POST /app/add */
export async function add1(body: API.AppAddRequest, options?: { [key: string]: any }) {
  return request<API.BaseResponseString>('/app/add', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 GET /app/chat/gen/code */
export async function chatToGenCode(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.chatToGenCodeParams,
  options?: { [key: string]: any }
) {
  return request<API.ServerSentEventString[]>('/app/chat/gen/code', {
    method: 'GET',
    params: {
      ...params,
    },
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 POST /app/cover/refresh */
export async function refreshCover(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.refreshCoverParams,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseString>('/app/cover/refresh', {
    method: 'POST',
    params: {
      ...params,
    },
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 DELETE /app/delete */
export async function deleteUsingDelete(body: API.DeleteRequest, options?: { [key: string]: any }) {
  return request<API.BaseResponseString>('/app/delete', {
    method: 'DELETE',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 POST /app/deploy */
export async function deploy(body: API.AppDeployRequest, options?: { [key: string]: any }) {
  return request<API.BaseResponseString>('/app/deploy', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 GET /app/download/${param0} */
export async function downloadAppCode(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.downloadAppCodeParams,
  options?: { [key: string]: any }
) {
  const { appId: param0, ...queryParams } = params
  return request<any>(`/app/download/${param0}`, {
    method: 'GET',
    params: { ...queryParams },
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 GET /app/getAppVoId */
export async function getappById(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.getappByIdParams,
  options?: { [key: string]: any }
) {
  return request<API.App>('/app/getAppVoId', {
    method: 'GET',
    params: {
      ...params,
    },
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 POST /app/list/page/app */
export async function listAppVoByPage(body: API.AppQueryRequest, options?: { [key: string]: any }) {
  return request<API.BaseResponsePageAppVo>('/app/list/page/app', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 POST /app/list/page/app_user/admin */
export async function listAppVoByPageByAdmin(
  body: API.AppQueryRequest,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponsePageAppUserVo>('/app/list/page/app_user/admin', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 POST /app/list/page/app_user/user */
export async function listAppVoByPageByUser(
  body: API.AppQueryRequest,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponsePageAppUserVo>('/app/list/page/app_user/user', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 POST /app/list/page/GoodApp */
export async function listGoodApp(body: API.AppQueryRequest, options?: { [key: string]: any }) {
  return request<API.BaseResponsePageAppUserVo>('/app/list/page/GoodApp', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 PUT /app/update */
export async function update1(body: API.AppUpdateRequest, options?: { [key: string]: any }) {
  return request<API.BaseResponseString>('/app/update', {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}
