/**
 * openapi2ts 生成类型的补充声明（本文件不在 @api 目录内，重新生成 api 不会覆盖它）。
 *
 * 背景：后端 GET /app/download/{appId} 没有在 Swagger 里声明路径参数
 * （后端 /v3/api-docs 中该接口的 parameters 为空数组），于是生成的
 * src/api/appController.ts 里引用了 API.downloadAppCodeParams，但
 * src/api/typings.d.ts 中并没有生成这个类型，`npm run type-check` 会报
 * TS2694: Namespace 'API' has no exported member 'downloadAppCodeParams'。
 * 这里在 @api 之外补上该类型，保证类型检查通过。
 *
 * 永久修法：给后端该方法补上 @Parameter/@Operation 注解后重新生成 api，
 * 届时可以删除本文件。
 */
declare namespace API {
  /** GET /app/download/{appId} 的非 body 参数 */
  type downloadAppCodeParams = {
    /** 应用 ID（后端雪花 ID 序列化为字符串，不做 Number() 转换） */
    appId: string | number
  }
}
