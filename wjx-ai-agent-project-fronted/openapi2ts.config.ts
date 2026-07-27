//作用（openapi)根据后端接口生成前端请求和Ts模型代码
export default {
  requestLibPath: "import request from '@/request'",//引入自定义的request(全局自定义请求拦截)
  schemaPath: 'http://localhost:8123/api/v3/api-docs',//后端接口文档（要先开启后端）
  serversPath: './src',//生成代码的位置目录
}
