package com.wjx.wjxaiagentprojectback.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;

import com.mybatisflex.core.query.QueryWrapper;

import com.mybatisflex.spring.service.impl.ServiceImpl;

import com.wjx.wjxaiagentprojectback.damon.dto.user.UserAddRequest;
import com.wjx.wjxaiagentprojectback.damon.dto.user.UserLoginRequest;
import com.wjx.wjxaiagentprojectback.damon.dto.user.UserQueryRequest;
import com.wjx.wjxaiagentprojectback.damon.dto.user.UserUpdateRequest;
import com.wjx.wjxaiagentprojectback.damon.entity.User;
import com.wjx.wjxaiagentprojectback.damon.enums.UserRoleEnum;
import com.wjx.wjxaiagentprojectback.damon.vo.UserVo;
import com.wjx.wjxaiagentprojectback.damon.vo.userLoginRequestVo;
import com.wjx.wjxaiagentprojectback.exception.BusinessException;
import com.wjx.wjxaiagentprojectback.exception.ErrorCode;
import com.wjx.wjxaiagentprojectback.mapper.UserMapper;
import com.wjx.wjxaiagentprojectback.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;

import java.util.ArrayList;
import java.util.List;

import static com.wjx.wjxaiagentprojectback.constant.UserConstant.USER_LOGIN_STATE;

/**
 * 用户 服务层实现。
 *
 * @author wjx
 * @since 2026-08-01
 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User>  implements UserService {
    /**
     * 登出
     */
    @Override
    public void LoginUserOut(HttpServletRequest request) {
        Object attribute = request.getSession().getAttribute(USER_LOGIN_STATE);
        if (attribute == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR, "用户未登录");
        }
        request.getSession().removeAttribute(USER_LOGIN_STATE);
    }

    /**
     * 通过对应的session,获取当前登录的用户
     * @param request
     * @return
     */
    @Override
    public User getLoginUser(HttpServletRequest request) {
        Object attribute = request.getSession().getAttribute(USER_LOGIN_STATE);
        User user= (User) attribute;
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR, "用户未登录");
        }
        //从数据库中查询用户信息
        User userFromDB = this.getById(user.getId());
        if (userFromDB == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR, "用户不存在");
        }
        return userFromDB;
    }

    /**
     * 用户登录
     * @param userLoginRequest
     * @param request
     * @return
     */
    @Override
    public userLoginRequestVo userLogin(UserLoginRequest userLoginRequest, HttpServletRequest request) {
        String userAccount = userLoginRequest.getUserAccount();
        String userPassword = userLoginRequest.getUserPassword();
        //1.校验参数,参数是否为空
        if (StrUtil.hasBlank(userAccount, userPassword)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "参数为空");
        }
        //2.账户密码的校验
        if (userPassword.length() < 8) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "用户密码不能小于8位");
        }
        //账户的校验
        if (userAccount.length() < 4) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "用户账号不能小于4位");
        }
        //3.密码加密(加盐)
        String userSecret =getUserPassword(userPassword);
        //4.查询用户
        QueryWrapper queryWrapper=new QueryWrapper();
        queryWrapper.eq("userAccount",userAccount);
        queryWrapper.eq("userPassword",userSecret);
        User user = this.mapper.selectOneByQuery(queryWrapper);
        if (user == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "用户不存在请先注册账号");
        }
        //更新session
        request.getSession().setAttribute(USER_LOGIN_STATE,user);
        //5.返回用户信息
        userLoginRequestVo userloginVo = new userLoginRequestVo();
        BeanUtil.copyProperties(user, userloginVo);
        return userloginVo;
    }

    /**
     * 用户注册
     * @param userAccount
     * @param userPassword
     * @param checkPassword
     * @return
     */
    @Override
    public long userRegister(String userAccount, String userPassword, String checkPassword) {
        //校验参数,参数是否为空
        if (StrUtil.hasBlank(userAccount, userPassword, checkPassword)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "参数为空");
        }
        //账户密码的校验
        if (userAccount.length() < 4) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "用户账号不能小于4位");
        }
        if (userPassword.length() < 8 || checkPassword.length() < 8) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "用户密码不能小于8位");
        }
        if (!userPassword.equals(checkPassword)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "两次密码不一致");
        }
        QueryWrapper queryWrapper=new QueryWrapper();
        queryWrapper.eq("userAccount",userAccount);
        //查询用户是否已经存在
        long count = this.mapper.selectCountByQuery(queryWrapper);
        if (count > 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "用户已存在");
        }
        //3.密码加密(加盐)
        String userSecret =getUserPassword(userPassword);
        //4.保存用户,返回用户id
        User user = new User();
        user.setUserAccount(userAccount);
        user.setUserPassword(userSecret);
        user.setUserRole(UserRoleEnum.USER.getValue());
        String Name= RandomUtil.randomNumbers(5);
        user.setUserName(Name+"创作者");
        boolean saveResult = this.save(user);
        if (!saveResult) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "用户注册失败");
        }
        //主键自动回填，ORM框架（如MyBatis-Plus）在执行完插入SQL后，会自动获取数据库生成的主键值，并将其设置回原来的 user 对象的 id 属性中
        return user.getId();
    }

    //密码加密(加盐)
    public String getUserPassword(String password) {
        final String salt = "wjx";
        return DigestUtils.md5DigestAsHex((password + salt).getBytes(StandardCharsets.UTF_8));

    }

    /**
     * 添加用户
     * @param userAddRequest
     * @return
     */
    @Override
    public void addUser(UserAddRequest userAddRequest) {
        User user = new User();
        BeanUtil.copyProperties(userAddRequest, user);
        //设置默认密码（不少于8位，否则与登录/注册校验“用户密码不能小于8位”冲突，新用户将无法登录）
        final String userPassword="12345678";
        String userPassword1 = getUserPassword(userPassword);
        user.setUserPassword(userPassword1);
        //保存用户
        this.save(user);
    }
    /**
     * 更新用户（更据id更新用户）
     * @param userUpdateRequest
     * @return
     */
    @Override
    public void updateUser(UserUpdateRequest userUpdateRequest) {
        //1.更据id查询用户
        Long id = userUpdateRequest.getId();
        //2.查询后校验
        User user = this.mapper.selectOneById(id);
        if( user == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "用户不存在");
        }
        //3.更新用户
        BeanUtil.copyProperties(userUpdateRequest, user);
        this.updateById(user);
    }
    /**
     * 删除用户
     * @param
     * @return
     */
    @Override
    public Boolean deleteUser(Long id) {
        return this.removeById(id);
    }

    /**
     * 获取用户列表，分页查询。
     * @param userQueryRequest
     * @return
     */
    @Override
    public QueryWrapper getQueryWrapper(UserQueryRequest userQueryRequest) {
        if(userQueryRequest==null){
            throw new BusinessException(ErrorCode.PARAM_ERROR, "参数为空");
        }
        //sortField：指定排序的字段名
        String sortField=userQueryRequest.getSortField();
        //ortOrder：指定排序的方向
        String sortOrder=userQueryRequest.getSortOrder();
        return QueryWrapper.create()
                .eq("id", userQueryRequest.getId())
                .eq("userRole", userQueryRequest.getUserRole())
                .like("userAccount", userQueryRequest.getUserAccount())
                .like("userName", userQueryRequest.getUserName())
                .like("userProfile", userQueryRequest.getUserProfile())
                .orderBy(sortField,"ascend".equals(sortOrder));
    }

    /**
     * 获取脱敏后的用户列表
     * @param records
     * @return
     */
    @Override
    public List<UserVo> getUserVoList(List<User> records) {
        List<UserVo> userList = new ArrayList<>();
        for (User user : records) {
            UserVo userVo = new UserVo();
            BeanUtil.copyProperties(user, userVo);
            userList.add(userVo);
        }
        return userList;
    }
    /**
     * 根据id获取用户信息
     */
    @Override
    public UserVo getUserVoById(Long id) {
        //1.根据id查询用户
        User user = this.getById(id);
        //2.校验
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        //3.返回用户信息
        UserVo userVo = new UserVo();
        BeanUtil.copyProperties(user, userVo);
        return userVo;
    }
}
