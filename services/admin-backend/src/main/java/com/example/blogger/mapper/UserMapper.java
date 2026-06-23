package com.example.blogger.mapper;

import com.example.blogger.entity.User;
import org.apache.ibatis.annotations.*;
import java.util.List;
import java.util.Map;

@Mapper
public interface UserMapper {
    @Select("SELECT id, username, status as status, phone, email, wx_id, wx_name as wxName, nick_name as nickName, track_limit as trackLimit, platform_limit as platformLimit, template, expire_date as expireDate, last_login as lastLogin, remark, can_set_email as canSetEmail, email_receive as emailReceive, membership_plan_id as membershipPlanId, invite_code as inviteCode, invited_by as invitedBy, user_type as userType, admin_id as adminId, is_deleted as isDeleted, created_at as createdAt, updated_at as updatedAt, style_config as styleConfig, theme_color as themeColor, title_font_size as titleFontSize, content_font_size as contentFontSize FROM tu_user WHERE is_deleted = 0 ORDER BY created_at DESC")
    List<User> findAll();

    @Select("SELECT id, username, password, status, phone, email, wx_id, wx_name as wxName, nick_name as nickName, ai_limit, track_limit as trackLimit, platform_limit as platformLimit, avatar, template, expire_date as expireDate, last_login as lastLogin, remark, can_set_email as canSetEmail, email_receive as emailReceive, membership_plan_id as membershipPlanId, invite_code as inviteCode, invited_by as invitedBy, user_type as userType, admin_id as adminId, is_deleted as isDeleted, created_at as createdAt, updated_at as updatedAt, style_config as styleConfig, theme_color as themeColor, title_font_size as titleFontSize, content_font_size as contentFontSize FROM tu_user WHERE id = #{id} AND is_deleted = 0")
    User findById(String id);

    @Select("SELECT id, username, password, status, phone, email, wx_id, wx_name as wxName, nick_name as nickName, ai_limit, track_limit as trackLimit, platform_limit as platformLimit, avatar, template, expire_date as expireDate, last_login as lastLogin, remark, can_set_email as canSetEmail, email_receive as emailReceive, membership_plan_id as membershipPlanId, invite_code as inviteCode, invited_by as invitedBy, user_type as userType, admin_id as adminId, is_deleted as isDeleted, created_at as createdAt, updated_at as updatedAt, style_config as styleConfig, theme_color as themeColor, title_font_size as titleFontSize, content_font_size as contentFontSize FROM tu_user WHERE username = #{username} AND is_deleted = 0")
    User findByUsername(String username);

    @Insert("INSERT INTO tu_user(id, username, password, status, phone, email, wx_id, wx_name, nick_name, ai_limit, track_limit, platform_limit, avatar, template, expire_date, last_login, remark, can_set_email, email_receive, membership_plan_id, invite_code, invited_by, user_type, admin_id, is_deleted, style_config, theme_color, title_font_size, content_font_size) VALUES(#{id}, #{username}, #{password}, #{status}, #{phone}, #{email}, #{wxId}, #{wxName}, #{nickName}, #{aiLimit}, #{trackLimit}, #{platformLimit}, #{avatar}, #{template}, #{expireDate}, #{lastLogin}, #{remark}, #{canSetEmail}, #{emailReceive}, #{membershipPlanId}, #{inviteCode}, #{invitedBy}, #{userType}, #{adminId}, 0, #{styleConfig}, #{themeColor}, #{titleFontSize}, #{contentFontSize})")
    int insert(User user);

    @Update("UPDATE tu_user SET username=#{username}, password=#{password}, status=#{status}, phone=#{phone}, email=#{email}, wx_id=#{wxId}, wx_name=#{wxName}, nick_name=#{nickName}, ai_limit=#{aiLimit}, track_limit=#{trackLimit}, platform_limit=#{platformLimit}, avatar=#{avatar}, template=#{template}, expire_date=#{expireDate}, last_login=#{lastLogin}, remark=#{remark}, can_set_email=#{canSetEmail}, email_receive=#{emailReceive}, membership_plan_id=#{membershipPlanId}, invite_code=#{inviteCode}, invited_by=#{invitedBy}, user_type=#{userType}, admin_id=#{adminId}, style_config=#{styleConfig}, theme_color=#{themeColor}, title_font_size=#{titleFontSize}, content_font_size=#{contentFontSize} WHERE id=#{id}")
    int update(User user);

    @Update("UPDATE tu_user SET last_login=#{lastLogin} WHERE id=#{id}")
    int updateLastLogin(@Param("id") String id, @Param("lastLogin") java.time.LocalDateTime lastLogin);

    @Update("UPDATE tu_user SET is_deleted = 1 WHERE id = #{id}")
    int delete(String id);

    @Update("UPDATE tu_user SET invite_code = #{inviteCode} WHERE id = #{id}")
    int updateInviteCode(@Param("id") String id, @Param("inviteCode") String inviteCode);

    @Select("SELECT id, username, password, status, phone, email, wx_id, wx_name as wxName, nick_name as nickName, ai_limit, track_limit as trackLimit, platform_limit as platformLimit, avatar, template, expire_date as expireDate, last_login as lastLogin, remark, can_set_email as canSetEmail, email_receive as emailReceive, membership_plan_id as membershipPlanId, invite_code as inviteCode, invited_by as invitedBy, user_type as userType, admin_id as adminId, is_deleted as isDeleted, created_at as createdAt, updated_at as updatedAt, style_config as styleConfig, theme_color as themeColor, title_font_size as titleFontSize, content_font_size as contentFontSize FROM tu_user WHERE invite_code = #{inviteCode} AND is_deleted = 0")
    User findByInviteCode(String inviteCode);

    @Select("SELECT COUNT(*) FROM tu_user WHERE is_deleted = 0")
    int countUsers();

    @Select("SELECT id, username, phone, email, wx_id, user_type as userType, admin_id as adminId FROM tu_user WHERE status = 1 AND is_deleted = 0 AND user_type IN (1, 2, 3) ORDER BY created_at DESC")
    List<Map<String, Object>> findAllActiveUsers();

    @Select("SELECT id, username, email, admin_id as adminId FROM tu_user WHERE status = 1 AND is_deleted = 0 AND user_type IN (1, 2, 3) AND email IS NOT NULL AND email != '' ORDER BY created_at DESC")
    List<Map<String, Object>> findAllActiveUsersWithEmail();

    @Update("<script>UPDATE tu_user SET admin_id = #{adminId} WHERE id IN <foreach collection='userIds' item='id' open='(' separator=',' close=')'>#{id}</foreach></script>")
    int batchUpdateAdminId(@Param("userIds") List<String> userIds, @Param("adminId") String adminId);

    @Select("SELECT u.id, u.username, u.email, u.phone, u.expire_date as expireDate, u.membership_plan_id as membershipPlanId, " +
            "u.admin_id as adminId, p.name as planName, p.price as planPrice " +
            "FROM tu_user u LEFT JOIN tu_membership_plan p ON u.membership_plan_id = p.id " +
            "WHERE u.is_deleted = 0 AND u.status = 1 AND u.expire_date IS NOT NULL " +
            "AND u.expire_date <= DATE_ADD(CURRENT_DATE, INTERVAL #{days} DAY) " +
            "ORDER BY u.expire_date ASC")
    List<Map<String, Object>> findExpiringUsers(@Param("days") int days);
}
