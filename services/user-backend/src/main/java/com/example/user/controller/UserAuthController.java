package com.example.user.controller;

import com.example.user.entity.MembershipPlan;
import com.example.user.entity.Result;
import com.example.user.entity.User;
import com.example.user.entity.UserTrack;
import com.example.user.mapper.MembershipPlanMapper;
import com.example.user.mapper.UserMapper;
import com.example.user.mapper.UserTrackMapper;
import com.example.user.service.UserService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
public class UserAuthController {
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final MembershipPlanMapper membershipPlanMapper;
    private final UserService userService;
    private final UserTrackMapper userTrackMapper;
    private final com.example.user.service.MailNotifyService mailNotifyService;

    public UserAuthController(UserMapper userMapper, PasswordEncoder passwordEncoder, MembershipPlanMapper membershipPlanMapper, UserService userService, UserTrackMapper userTrackMapper, com.example.user.service.MailNotifyService mailNotifyService) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.membershipPlanMapper = membershipPlanMapper;
        this.userService = userService;
        this.userTrackMapper = userTrackMapper;
        this.mailNotifyService = mailNotifyService;
    }

    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody Map<String, String> req) {
        String username = req.get("username");
        String password = req.get("password");
        User user = userMapper.findByUsername(username);
        if (user == null) {
            return Result.error("账号或密码错误");
        }

        String storedPassword = user.getPassword();
        boolean matched;
        if (storedPassword != null && storedPassword.startsWith("$2a$")) {
            matched = passwordEncoder.matches(password, storedPassword);
        } else {
            // 兼容旧明文密码：验证通过后自动升级为 BCrypt
            matched = password.equals(storedPassword);
            if (matched) {
                userMapper.updatePassword(user.getId(), passwordEncoder.encode(password));
            }
        }

        if (!matched) {
            return Result.error("账号或密码错误");
        }

        if (user.getStatus() == null || user.getStatus() != 1) {
            return Result.error("账号待审核，请联系客服");
        }
        userMapper.updateLastLogin(user.getId(), LocalDateTime.now());
        user.setLastLogin(LocalDateTime.now());
        Map<String, Object> data = new HashMap<>();
        data.put("token", "user-token-" + user.getId());
        data.put("user", user);
        if (user.getMembershipPlanId() != null && !user.getMembershipPlanId().isEmpty()) {
            MembershipPlan plan = membershipPlanMapper.findById(user.getMembershipPlanId());
            if (plan != null) {
                data.put("plan", plan);
            }
        }
        return Result.ok(data);
    }

    @PostMapping("/register")
    public Result<Map<String, Object>> register(@RequestBody Map<String, String> req) {
        String username = req.get("username");
        String password = req.get("password");
        String inviteCode = req.get("inviteCode");

        if (username == null || username.trim().isEmpty()) {
            return Result.error("用户名不能为空");
        }
        if (password == null || password.isEmpty()) {
            return Result.error("密码不能为空");
        }
        if (password.length() < 6) {
            return Result.error("密码长度不能少于6位");
        }

        User existing = userMapper.findByUsername(username.trim());
        if (existing != null) {
            return Result.error("用户名已被注册");
        }

        User user = new User();
        user.setUsername(username.trim());
        user.setPassword(passwordEncoder.encode(password));
        user.setStatus(0);
        user.setUserType(1);
        user.setAiLimit(50);
        user.setTrackLimit(0);
        user.setPlatformLimit("");
        user.setExpireDate(LocalDate.now().plusYears(1));
        user.setCanSetEmail(0);
        user.setEmailReceive(0);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());

        if (inviteCode != null && !inviteCode.trim().isEmpty()) {
            User inviter = userMapper.findByInviteCode(inviteCode.trim());
            if (inviter != null) {
                user.setInvitedBy(inviter.getId());
            }
        }

        userService.save(user);

        Map<String, Object> data = new HashMap<>();
        data.put("token", "user-token-" + user.getId());
        data.put("user", user);
        return Result.ok(data);
    }

    @PostMapping("/open-account")
    public Result<Map<String, Object>> openAccount(@RequestBody Map<String, Object> req) {
        String nickName = req.get("nickName") != null ? req.get("nickName").toString().trim() : "";
        String wxName = req.get("wxName") != null ? req.get("wxName").toString().trim() : "";
        String email = req.get("email") != null ? req.get("email").toString().trim() : "";
        String membershipPlanId = req.get("membershipPlanId") != null ? req.get("membershipPlanId").toString().trim() : "";
        String adminId = req.get("adminId") != null ? req.get("adminId").toString().trim() : "";
        @SuppressWarnings("unchecked")
        List<String> trackIds = req.get("trackIds") != null ? (List<String>) req.get("trackIds") : null;

        if (nickName.isEmpty()) {
            return Result.error("微信名称不能为空");
        }
        if (wxName.isEmpty()) {
            return Result.error("公众号名称不能为空");
        }
        if (email.isEmpty()) {
            return Result.error("邮箱不能为空");
        }
        if (trackIds == null || trackIds.isEmpty()) {
            return Result.error("请至少选择一个订阅赛道");
        }

        // 用户名和微信名称都对应微信名称
        String username = nickName;

        User existing = userMapper.findByUsername(username);
        if (existing != null) {
            return Result.error("该微信名称已被注册");
        }

        String defaultPassword = "Abc123456";

        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(defaultPassword));
        user.setNickName(nickName);
        user.setWxName(wxName);
        user.setEmail(email);
        user.setStatus(0);
        user.setUserType(1);
        user.setAiLimit(50);
        user.setTrackLimit(trackIds.size());
        user.setPlatformLimit("");
        user.setExpireDate(LocalDate.now().plusYears(1));
        user.setCanSetEmail(0);
        user.setEmailReceive(1);
        user.setTemplate("公众号标准模板");

        if (membershipPlanId != null && !membershipPlanId.isEmpty()) {
            user.setMembershipPlanId(membershipPlanId);
            MembershipPlan plan = membershipPlanMapper.findById(membershipPlanId);
            if (plan != null && plan.getExpireDays() != null && plan.getExpireDays() > 0) {
                user.setExpireDate(LocalDate.now().plusDays(plan.getExpireDays()));
            } else {
                user.setExpireDate(LocalDate.now().plusYears(1));
            }
        } else {
            user.setExpireDate(LocalDate.now().plusYears(1));
        }

        if (adminId != null && !adminId.isEmpty()) {
            user.setAdminId(adminId);
        }
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());

        userService.save(user);

        // Save track subscriptions
        for (String trackId : trackIds) {
            if (trackId != null && !trackId.isEmpty()) {
                UserTrack ut = new UserTrack();
                ut.setUserId(user.getId());
                ut.setTrackId(trackId);
                userTrackMapper.insert(ut);
            }
        }

        Map<String, Object> data = new HashMap<>();
        data.put("userId", user.getId());
        data.put("username", user.getUsername());
        data.put("password", defaultPassword);

        // 发送邮件通知管理员
        try {
            mailNotifyService.sendOpenAccountNotice(nickName, wxName, email);
        } catch (Exception e) {
            // 通知失败不影响开户结果
            System.err.println("[openAccount] 发送邮件通知失败: " + e.getMessage());
        }

        return Result.ok(data);
    }

    @PostMapping("/change-password")
    public Result<Void> changePassword(@RequestBody Map<String, String> req) {
        String userId = req.get("userId");
        String oldPassword = req.get("oldPassword");
        String newPassword = req.get("newPassword");

        if (userId == null || userId.isEmpty()) {
            return Result.error("用户未登录");
        }
        if (oldPassword == null || oldPassword.isEmpty()) {
            return Result.error("请输入原密码");
        }
        if (newPassword == null || newPassword.isEmpty()) {
            return Result.error("请输入新密码");
        }
        if (newPassword.length() < 6) {
            return Result.error("新密码长度不能少于6位");
        }

        User user = userMapper.findById(userId);
        if (user == null) {
            return Result.error("用户不存在");
        }

        String storedPassword = user.getPassword();
        boolean matched;
        if (storedPassword != null && storedPassword.startsWith("$2a$")) {
            matched = passwordEncoder.matches(oldPassword, storedPassword);
        } else {
            matched = oldPassword.equals(storedPassword);
        }

        if (!matched) {
            return Result.error("原密码错误");
        }

        userMapper.updatePassword(userId, passwordEncoder.encode(newPassword));
        return Result.ok(null);
    }
}
