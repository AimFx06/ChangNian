package com.changnian.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.changnian.auth.CurrentUser;
import com.changnian.auth.Role;
import com.changnian.auth.TokenService;
import com.changnian.auth.UserContext;
import com.changnian.common.BizException;
import com.changnian.domain.entity.FamilyEntity;
import com.changnian.domain.entity.UserEntity;
import com.changnian.domain.mapper.FamilyMapper;
import com.changnian.domain.mapper.UserMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;

@Service
public class AuthService {
    private final UserMapper userMapper;
    private final FamilyMapper familyMapper;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;

    public AuthService(UserMapper userMapper, FamilyMapper familyMapper, PasswordEncoder passwordEncoder, TokenService tokenService) {
        this.userMapper = userMapper;
        this.familyMapper = familyMapper;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
    }

    @Transactional
    public AuthResult register(RegisterCommand command) {
        assertUsernameAvailable(command.username());
        Role role = parseRole(command.role());
        FamilyEntity family = resolveFamily(role, command.familyCode(), command.displayName());

        UserEntity user = new UserEntity();
        user.setFamilyId(family.getId());
        user.setUsername(command.username());
        user.setPasswordHash(passwordEncoder.encode(command.password()));
        user.setDisplayName(command.displayName());
        user.setRole(role.name());
        user.setPhone(command.phone());
        user.setGender(normalizeGender(command.gender()));
        user.setCreatedAt(LocalDateTime.now());
        userMapper.insert(user);
        return toResult(user, family);
    }

    public AuthResult login(String username, String password) {
        UserEntity user = userMapper.selectOne(Wrappers.<UserEntity>lambdaQuery()
                .eq(UserEntity::getUsername, username));
        if (user == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new BizException("账号或密码错误");
        }
        FamilyEntity family = familyMapper.selectById(user.getFamilyId());
        return toResult(user, family);
    }

    @Transactional
    public AuthResult updateProfile(String displayName, String phone, String gender) {
        CurrentUser currentUser = UserContext.get();
        UserEntity user = userMapper.selectById(currentUser.id());
        if (user == null) {
            throw new BizException("用户不存在");
        }
        String name = displayName == null ? "" : displayName.trim();
        if (name.isBlank()) {
            throw new BizException("姓名不能为空");
        }
        user.setDisplayName(name);
        user.setPhone(phone == null ? "" : phone.trim());
        user.setGender(normalizeGender(gender));
        userMapper.updateById(user);
        FamilyEntity family = familyMapper.selectById(user.getFamilyId());
        return toResult(user, family);
    }

    private AuthResult toResult(UserEntity user, FamilyEntity family) {
        CurrentUser currentUser = new CurrentUser(
                user.getId(),
                user.getFamilyId(),
                Role.valueOf(user.getRole()),
                user.getUsername(),
                user.getDisplayName()
        );
        return new AuthResult(tokenService.create(currentUser), UserView.from(user, family));
    }

    private void assertUsernameAvailable(String username) {
        Long count = userMapper.selectCount(Wrappers.<UserEntity>lambdaQuery()
                .eq(UserEntity::getUsername, username));
        if (count > 0) {
            throw new BizException("用户名已存在");
        }
    }

    private FamilyEntity resolveFamily(Role role, String familyCode, String displayName) {
        if (familyCode != null && !familyCode.isBlank()) {
            FamilyEntity family = familyMapper.selectOne(Wrappers.<FamilyEntity>lambdaQuery()
                    .eq(FamilyEntity::getInviteCode, familyCode.trim()));
            if (family == null) {
                throw new BizException("家庭邀请码不存在");
            }
            return family;
        }
        if (role == Role.ELDER) {
            throw new BizException("老人账号注册需要填写家庭邀请码");
        }
        FamilyEntity family = new FamilyEntity();
        family.setName(displayName + "的家庭");
        family.setInviteCode("CN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT));
        family.setCreatedAt(LocalDateTime.now());
        familyMapper.insert(family);
        return family;
    }

    private Role parseRole(String role) {
        try {
            return Role.valueOf(role.toUpperCase(Locale.ROOT));
        } catch (Exception exception) {
            throw new BizException("角色只能是 CHILD 或 ELDER");
        }
    }

    private String normalizeGender(String gender) {
        if (gender == null || gender.isBlank()) {
            return "";
        }
        String value = gender.trim();
        if (!"男".equals(value) && !"女".equals(value)) {
            throw new BizException("性别只能是男或女");
        }
        return value;
    }

    public record RegisterCommand(String username, String password, String displayName, String role, String phone, String gender, String familyCode) {
    }

    public record AuthResult(String token, UserView user) {
    }

    public record UserView(Long id, Long familyId, String username, String displayName, String role, String phone, String gender, String familyName, String inviteCode) {
        public static UserView from(UserEntity user, FamilyEntity family) {
            return new UserView(
                    user.getId(),
                    user.getFamilyId(),
                    user.getUsername(),
                    user.getDisplayName(),
                    user.getRole(),
                    user.getPhone(),
                    user.getGender(),
                    family == null ? "" : family.getName(),
                    family == null ? "" : family.getInviteCode()
            );
        }
    }
}
