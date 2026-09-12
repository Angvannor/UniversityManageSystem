package com.hbk.activity.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 密码加密工具类。
 *
 * <p>【为什么不能存明文密码】
 * 实验报告把「密码不以明文保存」列为重要约束。如果数据库里直接存 123456，
 * 任何能看到数据库的人（包括数据库管理员、备份文件泄露）都能直接拿到所有人的密码。
 * 而且很多人在不同网站用同一个密码，泄露影响会扩散。
 *
 * <p>【本项目采用的做法：SHA-256 加盐哈希】
 * <ol>
 *   <li><b>哈希</b>：用 SHA-256 把密码算成一串固定长度的十六进制字符串。
 *       这个过程是<b>单向</b>的 —— 能从密码算出摘要，但无法从摘要反推出密码；</li>
 *   <li><b>加盐</b>：算哈希前在密码前面拼上一段固定的"盐"（salt）。
 *       这样即使两个用户密码相同，摘要也和不加盐时不同，
 *       攻击者也不能用网上现成的"密码-摘要"对照表（彩虹表）直接反查。</li>
 * </ol>
 *
 * <p>【哈希与加密的区别（面试常问）】
 * <ul>
 *   <li>加密是可逆的：有密钥就能还原原文（比如 AES）；</li>
 *   <li>哈希是单向的：无法还原原文，只能"重新算一遍再比对"。</li>
 * </ul>
 * 密码只需要"验证是否一致"，不需要还原，所以用哈希而不是加密。
 *
 * <p>【本项目的取舍说明】
 * 这里用的是"固定盐"，够作业项目使用，实现也简单。
 * 真实项目会用每个用户一个随机盐（把盐和摘要一起存进数据库），
 * 或者使用 BCrypt / Argon2 这类专门为密码设计的慢哈希算法。
 *
 * <p>【使用方法】
 * <pre>
 *   // 注册时：把明文转成密文再存库
 *   String encrypted = PasswordUtil.encrypt("123456");
 *   // 登录时：把用户输入的明文和库里的密文比对
 *   boolean ok = PasswordUtil.matches("123456", user.getPassword());
 * </pre>
 *
 * @author HBK组
 */
public final class PasswordUtil {

    /**
     * 盐值：参与哈希计算的一段固定字符串。
     * 它不需要保密（就算泄露，也只是让攻击者多算一步），
     * 作用主要是防止通用的彩虹表直接命中。
     */
    private static final String SALT = "hbk-campus-activity-system-v1-";

    /** 私有构造方法：工具类不允许被实例化 */
    private PasswordUtil() {
    }

    /**
     * 把明文密码转换成密文（SHA-256 加盐哈希）。
     *
     * @param rawPassword 明文密码，不能为 null
     * @return 64 位十六进制字符串（SHA-256 摘要固定 32 字节 = 64 个十六进制字符）
     */
    public static String encrypt(String rawPassword) {
        if (rawPassword == null) {
            throw new IllegalArgumentException("密码不能为 null");
        }
        try {
            // 1. 取得 SHA-256 摘要算法的实例
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            // 2. 加盐后转成字节数组（统一用 UTF-8，避免不同平台编码不一致）
            byte[] input = (SALT + rawPassword).getBytes(StandardCharsets.UTF_8);

            // 3. 计算摘要，得到 32 字节的结果
            byte[] hash = digest.digest(input);

            // 4. 转成十六进制字符串，方便存进 VARCHAR 字段
            return toHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 是 JDK 标准算法，正常环境不会走到这里
            throw new IllegalStateException("当前 JDK 不支持 SHA-256 算法", e);
        }
    }

    /**
     * 校验明文密码与数据库中的密文是否匹配。
     *
     * <p>做法不是"解密"，而是把用户输入的明文重新哈希一遍，再和密文比较。
     *
     * @param rawPassword      用户本次输入的明文密码
     * @param encryptedPassword 数据库中保存的密文
     * @return true 表示密码正确
     */
    public static boolean matches(String rawPassword, String encryptedPassword) {
        if (rawPassword == null || encryptedPassword == null || encryptedPassword.isEmpty()) {
            return false;
        }
        // 两边都是我们自己生成的十六进制字符串，用忽略大小写的比较更保险
        return encrypt(rawPassword).equalsIgnoreCase(encryptedPassword);
    }

    /**
     * 把字节数组转成小写十六进制字符串。
     *
     * <p>为什么需要这一步：摘要结果是二进制字节，直接转成字符串会出现乱码和
     * 不可见字符，存进数据库也容易出错；转成十六进制后只包含 0-9 和 a-f，安全可靠。
     *
     * @param bytes 待转换的字节数组
     * @return 十六进制字符串
     */
    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            // 一个字节 8 位，用两位十六进制表示；& 0xFF 是为了把负数转成 0~255
            String hex = Integer.toHexString(b & 0xFF);
            if (hex.length() == 1) {
                sb.append('0');          // 不足两位前面补 0，保证长度固定
            }
            sb.append(hex);
        }
        return sb.toString();
    }

    /**
     * 自测入口：打印指定密码的密文，用于生成 db/schema.sql 里的初始账号密码。
     *
     * @param args 第 0 个参数为待加密的明文，缺省为 123456
     */
    public static void main(String[] args) {
        String raw = (args != null && args.length > 0) ? args[0] : "123456";
        String encrypted = encrypt(raw);

        System.out.println("明文密码 : " + raw);
        System.out.println("密文     : " + encrypted);
        System.out.println("长度     : " + encrypted.length() + " 位十六进制字符");
        System.out.println("自校验   : " + matches(raw, encrypted));
        System.out.println("错误密码 : " + matches("wrong-password", encrypted));
    }
}
