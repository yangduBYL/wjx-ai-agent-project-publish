// @ts-ignore
/* eslint-disable */
import request from '@/request'

/** 此处后端没有提供注释 POST /user/add */
export async function add(body: API.UserAddRequest, options?: { [key: string]: any }) {
  return request<API.BaseResponseString>('/user/add', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 DELETE /user/delete */
export async function remove(body: API.DeleteRequest, options?: { [key: string]: any }) {
  return request<API.BaseResponseBoolean>('/user/delete', {
    method: 'DELETE',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 GET /user/get/${param0} */
export async function getUserById(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.getUserByIdParams,
  options?: { [key: string]: any }
) {
  const { id: param0, ...queryParams } = params
  return request<API.BaseResponseUserVo>(`/user/get/${param0}`, {
    method: 'GET',
    params: { ...queryParams },
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 GET /user/getLoginUser */
export async function getLoginUser(options?: { [key: string]: any }) {
  return request<API.BaseResponseUser>('/user/getLoginUser', {
    method: 'GET',
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 POST /user/list/page/vo */
export async function listUserVoByPage(
  body: API.UserQueryRequest,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponsePageUserVo>('/user/list/page/vo', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 POST /user/login */
export async function login(body: API.UserLoginRequest, options?: { [key: string]: any }) {
  return request<API.BaseResponseUserLoginRequestVo>('/user/login', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 POST /user/loginOut */
export async function loginOut(options?: { [key: string]: any }) {
  return request<API.BaseResponseString>('/user/loginOut', {
    method: 'POST',
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 POST /user/register */
export async function register(body: API.UserRegisterRequest, options?: { [key: string]: any }) {
  return request<API.BaseResponseLong>('/user/register', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 POST /user/update */
export async function update2(body: API.UserUpdateRequest, options?: { [key: string]: any }) {
  return request<API.BaseResponseString>('/user/update', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}
