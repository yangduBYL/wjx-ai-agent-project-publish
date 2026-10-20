package com.wjx.wjxaiagentprojectback.controller;

import com.mybatisflex.core.paginate.Page;
import com.wjx.wjxaiagentprojectback.annotation.AuthCheck;
import com.wjx.wjxaiagentprojectback.common.BaseResponse;
import com.wjx.wjxaiagentprojectback.common.DeleteRequest;
import com.wjx.wjxaiagentprojectback.common.ResultUtils;
import com.wjx.wjxaiagentprojectback.constant.UserConstant;


import com.wjx.wjxaiagentprojectback.damon.dto.user.*;
import com.wjx.wjxaiagentprojectback.damon.entity.User;
import com.wjx.wjxaiagentprojectback.damon.vo.UserVo;
import com.wjx.wjxaiagentprojectback.damon.vo.userLoginRequestVo;

import com.wjx.wjxaiagentprojectback.exception.ErrorCode;
import com.wjx.wjxaiagentprojectback.exception.ThrowUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.beans.factory.annotation.Autowired;

import com.wjx.wjxaiagentprojectback.service.UserService;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

/**
 * 用户 控制层。
 *
 * @author wjx
 * @since 2026-08-01
 */
@RestController
@RequestMapping("/user")
public class UserController {

    @Autowired
    private UserService userService;
    /**
     * 登出用户
     */
    @PostMapping("/loginOut")
    public BaseResponse<String> loginOut(HttpServletRequest request) {
        userService.LoginUserOut(request);
        return ResultUtils.success("登出成功");
    }
    /**
     * 获取登录用户信息
     *
     */
    @GetMapping("/getLoginUser")
    public BaseResponse<User> getLoginUser(HttpServletRequest request) {
        User user = userService.getLoginUser(request);
        return ResultUtils.success(user);
    }
    /**
     * 用户登录
     */
    @PostMapping("/login")
    public BaseResponse<userLoginRequestVo> login(@RequestBody UserLoginRequest userLoginRequest, HttpServletRequest request) {
        //1.先对请求进行判断
        ThrowUtils.throwIf(userLoginRequest==null, ErrorCode.PARAM_ERROR);
        //2.对请求参数进行校验
        userLoginRequestVo userloginvo=userService.userLogin(userLoginRequest,request);
        return ResultUtils.success(userloginvo);
    }
    /**
     * 用户注册
     */
    @PostMapping("/register")
    public BaseResponse<Long> register(@RequestBody UserRegisterRequest userRegisterRequest) {
        //1.先对请求进行判断
        ThrowUtils.throwIf(userRegisterRequest==null, ErrorCode.PARAM_ERROR);
        //2.对请求参数进行校验
        String userPassword = userRegisterRequest.getUserPassword();
        String userAccount = userRegisterRequest.getUserAccount();
        String checkPassword = userRegisterRequest.getCheckPassword();
        long result_id = userService.userRegister(userAccount, userPassword, checkPassword);
        return ResultUtils.success(result_id);
    }
//    /**
//     * 保存用户。
//     *
//     * @param user 用户
//     * @return {@code true} 保存成功，{@code false} 保存失败
//     */
//    @PostMapping("/save")
//    public boolean save(@RequestBody User user) {
//        return userService.save(user);
//    }
//
//    /**
//     * 根据主键删除用户。
//     *
//     * @param id 主键
//     * @return {@code true} 删除成功，{@code false} 删除失败
//     */
//    @DeleteMapping("/remove/{id}")
//    public boolean remove(@PathVariable Long id) {
//        return userService.removeById(id);
//    }
//
//    /**
//     * 根据主键更新用户。
//     *
//     * @param user 用户
//     * @return {@code true} 更新成功，{@code false} 更新失败
//     */
//    @PutMapping("/update")
//    public boolean update(@RequestBody User user) {
//        return userService.updateById(user);
//    }
//
//    /**
//     * 查询所有用户。
//     *
////     * @return 所有数据
//     */
//    @GetMapping("/list")
//    public List<User> list() {
//        return userService.list();
//    }
//
//    /**
//     * 根据主键获取用户。
//     *
//     * @param id 用户主键
//     * @return 用户详情
//     */
//    @GetMapping("/getInfo/{id}")
//    public User getInfo(@PathVariable Long id) {
//        return userService.getById(id);
//    }
//
//    /**
//     * 分页查询用户。
//     *
//     * @param page 分页对象
//     * @return 分页对象
//     */
//    @GetMapping("/page")
//    public Page<User> page(Page<User> page) {
//        return userService.page(page);
//    }
    /**
     * 添加用户
     */
    @PostMapping("/add")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<String> add(@RequestBody UserAddRequest userAddRequest) {
       ThrowUtils.throwIf(userAddRequest==null, ErrorCode.PARAM_ERROR);
        userService.addUser(userAddRequest);
        return ResultUtils.success("添加用户成功");
    }
    /**
     * 分页查询用户
     */
    @PostMapping("/list/page/vo")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<UserVo>> listUserVoByPage(@RequestBody UserQueryRequest userQueryRequest) {
        ThrowUtils.throwIf(userQueryRequest==null, ErrorCode.PARAM_ERROR);
        //获取当前页号
        long pageNum = userQueryRequest.getPageNum();
        //获取每页展示数据大小
        long pageSize = userQueryRequest.getPageSize();
        //获取分页查询结果
        Page<User> userPage = userService.page(Page.of(pageNum, pageSize),
                userService.getQueryWrapper(userQueryRequest));
        //数据脱敏
        //1.新建
        Page<UserVo> userVoPage = new Page<>(pageNum, pageSize, userPage.getTotalRow());
        //2.数据脱敏处理
        List<UserVo> userVoList = userService.getUserVoList(userPage.getRecords());
        //3.设置数据
        userVoPage.setRecords(userVoList);
        //4.返回结果
        return ResultUtils.success(userVoPage);
    }


    /**
     * 修改用户/更新用户
     */
    @PostMapping("/update")
    public BaseResponse<String> update(@RequestBody UserUpdateRequest userUpdateRequest) {
        ThrowUtils.throwIf(userUpdateRequest==null, ErrorCode.PARAM_ERROR);
        userService.updateUser(userUpdateRequest);
        return ResultUtils.success("更新成功");
    }
    /**
     //     * 根据主键删除用户。
     //     *
     //     * @param id 主键
     //     * @return {@code true} 删除成功，{@code false} 删除失败
     //     */
    @DeleteMapping("/delete")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> remove(@RequestBody DeleteRequest deleteRequest) {
        ThrowUtils.throwIf(deleteRequest==null, ErrorCode.PARAM_ERROR);
        Long id = deleteRequest.getId();
        Boolean b=userService.deleteUser(id);
        return ResultUtils.success(b);
    }
    /**
     * 根据id获取用户信息；
     */
    @GetMapping("/get/{id}")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<UserVo> getUserById(@PathVariable("id") Long id) {
        ThrowUtils.throwIf(id==null, ErrorCode.PARAM_ERROR);
        UserVo userVo = userService.getUserVoById(id);
        return ResultUtils.success(userVo);
    }

}
