package com.hbk.activity.tool;

import com.hbk.activity.dao.UserDAO;
import com.hbk.activity.entity.User;

/**
 * UserDAO 手工测试类（临时验证用，不属于系统正式功能）。
 */
public class UserDaoTest {

    public static void main(String[] args) {
        UserDAO dao = new UserDAO();

        // 1) 查已存在的账号
        System.out.println("teacher01 是否存在: " + dao.existsByUsername("teacher01"));  // 期望 true
        System.out.println("nobody 是否存在: " + dao.existsByUsername("nobody"));       // 期望 false

        // 2) 按账号查
        User u = dao.findByUsername("student01");
        System.out.println("查到的用户: " + u);      // 期望打印 student01 / 李同学 / STUDENT

        // 3) 按 id 查（student01 的 id 是 2）
        System.out.println("按id查: " + dao.findById(2L));

        // 4) 新增一个测试账号（密码先存明文，加密功能后面做）
        User newUser = new User();
        newUser.setUsername("test_user_01");
        newUser.setPassword("123456");
        newUser.setName("测试用户");
        newUser.setRole("STUDENT");
        int rows = dao.insert(newUser);
        System.out.println("新增影响行数: " + rows);   // 期望 1

        // 5) 再查一次，验证真的插进去了
        System.out.println("新增后查询: " + dao.findByUsername("test_user_01"));

        // 6) 再插一次同名账号 —— 会触发数据库唯一约束，用来观察异常
        System.out.println("重复插入影响行数: " + dao.insert(newUser));  // 期望 0，并打印异常
    }
}