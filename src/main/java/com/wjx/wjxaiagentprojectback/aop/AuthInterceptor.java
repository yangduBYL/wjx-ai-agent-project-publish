package com.wjx.wjxaiagentprojectback.aop;

import com.wjx.wjxaiagentprojectback.annotation.AuthCheck;
import com.wjx.wjxaiagentprojectback.damon.entity.User;
import com.wjx.wjxaiagentprojectback.damon.enums.UserRoleEnum;
import com.wjx.wjxaiagentprojectback.exception.BusinessException;
import com.wjx.wjxaiagentprojectback.exception.ErrorCode;
import com.wjx.wjxaiagentprojectback.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 权限控制的实现
 *
 */
@Aspect//标识为切面类
@Component
public class AuthInterceptor {
    @Resource
    private UserService userService;
    @Around("@annotation(authCheck)")
    //                      joinpoint，切入点
    public Object doAround(ProceedingJoinPoint joinPoint, AuthCheck authCheck) throws Throwable {
        //获取当前的方法的属性值
        String mustRole = authCheck.mustRole();
        /**
         * RequestContextHolder：这是 Spring Web 提供的一个请求上下文持有者工具类。它的底层本质上是利用 ThreadLocal 技术，将当前线程（即处理当前 HTTP 请求的线程）绑定的请求上下文对象保存起来。
         * currentRequestAttributes()：调用这个静态方法，可以从 ThreadLocal 中取出当前线程关联的 RequestAttributes 对象。
         */
        RequestAttributes requestAttributes = RequestContextHolder.currentRequestAttributes();
        //获取当前请求的request对象
        /**
         * (ServletRequestAttributes) requestAttributes：RequestAttributes 是一个接口，它抽象了各种不同类型的请求上下文（甚至包括非 HTTP 的 Portlet 请求）。但在普通的 Spring Web 开发中，它的实际实现类是 ServletRequestAttributes。因此，这里需要进行强转（向下转型）。
         * .getRequest()：转成 ServletRequestAttributes 后，调用它的 getRequest() 方法，就能真正拿到底层原生的 HttpServletRequest 对象了。
         */
        HttpServletRequest request = ((ServletRequestAttributes) requestAttributes).getRequest();
        //获取当前登录的用户信息
        User loginuser= userService.getLoginUser(request);
        //所需权限获取,用枚举值对比
        UserRoleEnum mustRoleEnum = UserRoleEnum.getEnumByValue(mustRole);
        //如果说，无需权限
        if(mustRoleEnum==null){
            return joinPoint.proceed();
        }
        //需要权限,登陆用户权限与所需权限对比
        UserRoleEnum userRoleEnum = UserRoleEnum.getEnumByValue(loginuser.getUserRole());
        //用户角色为空，没登陆，没有权限
        if(userRoleEnum==null){
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        //用户角色不为空，有权限，但是权限不够
        //1.要求是管理员权限时
        if(!mustRoleEnum.equals(userRoleEnum)){
            if(!UserRoleEnum.ADMIN.equals(mustRoleEnum)&&UserRoleEnum.ADMIN.equals(userRoleEnum)){
                return joinPoint.proceed();
            }
            throw new BusinessException(ErrorCode.NOT_AUTH_ERROR);
        }
        //权限校验通过，放行
        return joinPoint.proceed();
    }
}
