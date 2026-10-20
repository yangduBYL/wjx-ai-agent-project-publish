package com.wjx.wjxaiagentprojectback.service;

import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.service.IService;

import com.wjx.wjxaiagentprojectback.damon.dto.user.UserAddRequest;
import com.wjx.wjxaiagentprojectback.damon.dto.user.UserLoginRequest;
import com.wjx.wjxaiagentprojectback.damon.dto.user.UserQueryRequest;
import com.wjx.wjxaiagentprojectback.damon.dto.user.UserUpdateRequest;
import com.wjx.wjxaiagentprojectback.damon.entity.User;
import com.wjx.wjxaiagentprojectback.damon.vo.UserVo;
import com.wjx.wjxaiagentprojectback.damon.vo.userLoginRequestVo;

import jakarta.servlet.http.HttpServletRequest;

import java.util.List;


/**
 * 用户 服务层。
 *
 * @author wjx
 * @since 2026-08-01
 */
public interface UserService extends IService<User> {
    void LoginUserOut(HttpServletRequest request);


    /**
     * 获取登录用户信息
     * @param request
     * @return
     */
    User getLoginUser(HttpServletRequest request);

    /**
     * 用户登录
     * @param userLoginRequest
     * @param request
     * @return
     */
    userLoginRequestVo userLogin(UserLoginRequest userLoginRequest, HttpServletRequest request);

    /**
     * 用户注册
     * @param userAccount
     * @param userPassword
     * @param checkPassword
     * @return
     */
    public long userRegister(String userAccount, String userPassword, String checkPassword);

    /**
     * 添加用户
     *
     */
    void addUser(UserAddRequest userAddRequest);



    /**
     * 更新用户
     * @param userUpdateRequest
     * @return
     */
    void updateUser(UserUpdateRequest userUpdateRequest);

    /***
     * 更据删除用户
     * @param id
     * @return
     */
    Boolean deleteUser(Long id);

    /**
     * 查询用户
     * @param userQueryRequest
     * @return
     */
    public QueryWrapper getQueryWrapper(UserQueryRequest userQueryRequest);


    /**
     * 获取脱敏后的用户列表
     * @param records
     * @return
     */
    List<UserVo> getUserVoList(List<User> records);

    /**
     * 根据id获取用户
     * @param id
     * @return
     */
    UserVo getUserVoById(Long id);
}
