package com.example.blogger.service;

import com.example.blogger.entity.EmailPushLog;
import com.example.blogger.entity.TitleLibrary;
import com.example.blogger.entity.TitleRecommendation;
import com.example.blogger.entity.Track;
import com.example.blogger.entity.User;
import com.example.blogger.entity.UserTrack;
import com.example.blogger.entity.Config;
import com.example.blogger.mapper.ConfigMapper;
import com.example.blogger.mapper.EmailPushLogMapper;
import com.example.blogger.mapper.TitleLibraryMapper;
import com.example.blogger.mapper.TitleRecommendationMapper;
import com.example.blogger.mapper.TrackMapper;
import com.example.blogger.mapper.UserMapper;
import com.example.blogger.mapper.UserTrackMapper;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class TitleLibraryService {
    private static final Logger log = LoggerFactory.getLogger(TitleLibraryService.class);

    private static final List<String> CARD_STYLES = Arrays.asList(
        "xiaohongshu", "wechat", "douyin", "literary", "minimal", "business"
    );

    private final TitleLibraryMapper titleLibraryMapper;
    private final TitleRecommendationMapper titleRecommendationMapper;
    private final UserMapper userMapper;
    private final EmailPushLogMapper emailPushLogMapper;
    private final UserTrackMapper userTrackMapper;
    private final TrackMapper trackMapper;
    private final EmailService emailService;
    private final ConfigMapper configMapper;

    public TitleLibraryService(TitleLibraryMapper titleLibraryMapper, TitleRecommendationMapper titleRecommendationMapper,
                               UserMapper userMapper, EmailPushLogMapper emailPushLogMapper,
                               UserTrackMapper userTrackMapper, TrackMapper trackMapper,
                               EmailService emailService, ConfigMapper configMapper) {
        this.titleLibraryMapper = titleLibraryMapper;
        this.titleRecommendationMapper = titleRecommendationMapper;
        this.userMapper = userMapper;
        this.emailPushLogMapper = emailPushLogMapper;
        this.userTrackMapper = userTrackMapper;
        this.trackMapper = trackMapper;
        this.emailService = emailService;
        this.configMapper = configMapper;
    }

    /**
     * 启动时自动填充标题库中 platform 为空的记录
     * 根据 track_id 查找赛道，取赛道的第一个 platform 填充
     */
    @PostConstruct
    public void initFillEmptyPlatform() {
        List<TitleLibrary> emptyList = titleLibraryMapper.findByEmptyPlatform();
        int filled = 0;
        int skipped = 0;
        for (TitleLibrary tl : emptyList) {
            String trackId = tl.getTrackId();
            if (trackId == null || trackId.isEmpty()) {
                skipped++;
                System.out.println("[启动填充platform] 跳过(无trackId): " + tl.getTitle());
                continue;
            }
            Track track = trackMapper.findById(trackId);
            if (track == null || track.getPlatforms() == null || track.getPlatforms().isEmpty()) {
                skipped++;
                System.out.println("[启动填充platform] 跳过(赛道无platform): " + tl.getTitle() + " trackId=" + trackId);
                continue;
            }
            String platform = track.getPlatforms();
            if (platform == null || platform.isEmpty()) {
                skipped++;
                continue;
            }
            titleLibraryMapper.updatePlatform(tl.getId(), platform);
            filled++;
            System.out.println("[启动填充platform] 填充: " + tl.getTitle() + " -> " + platform);
        }
        System.out.println("[启动填充platform] 完成: 填充=" + filled + ", 跳过=" + skipped);
    }

    /* TODO: 临时逻辑 —— 启动时删除 track_id 指向已不存在赛道的脏数据标题，下次发布前请注释掉下面整个方法 */
    @PostConstruct
    public void initDeleteOrphanTitles() {
        int deleted = titleLibraryMapper.deleteOrphanTitles();
        System.out.println("[临时] 启动自动删除孤儿标题(track_id指向已删除赛道): " + deleted + " 条");
    }
    /* TODO: 临时逻辑结束 —— 下次请注释掉上面整个方法 */

    public List<TitleLibrary> list() {
        return titleLibraryMapper.findAll();
    }

    public Map<String, Object> listPage(int page, int pageSize) {
        int offset = (page - 1) * pageSize;
        List<TitleLibrary> list = titleLibraryMapper.findAllPage(offset, pageSize);
        int total = titleLibraryMapper.countAll();
        Map<String, Object> result = new HashMap<>();
        result.put("list", list);
        result.put("total", total);
        return result;
    }

    public List<TitleLibrary> search(String platform, String trackId, String keyword, String recommendUserName, String matched, String pushDate, String isUsed, String isConfirmed, String aiFlavor, String userType, String matchable, String sortField, String sortOrder) {
        return titleLibraryMapper.search(platform, trackId, keyword, recommendUserName, matched, pushDate, isUsed, isConfirmed, aiFlavor, userType, matchable, sortField, sortOrder);
    }

    public Map<String, Object> searchPage(String platform, String trackId, String keyword, String recommendUserName, String matched, String pushDate, String isUsed, String isConfirmed, String aiFlavor, String userType, String matchable, int page, int pageSize, String sortField, String sortOrder) {
        int offset = (page - 1) * pageSize;
        List<TitleLibrary> list = titleLibraryMapper.searchPage(platform, trackId, keyword, recommendUserName, matched, pushDate, isUsed, isConfirmed, aiFlavor, userType, matchable, offset, pageSize, sortField, sortOrder);
        int total = titleLibraryMapper.countSearch(platform, trackId, keyword, recommendUserName, matched, pushDate, isUsed, isConfirmed, aiFlavor, userType, matchable);
        Map<String, Object> result = new HashMap<>();
        result.put("list", list);
        result.put("total", total);
        return result;
    }

    public TitleLibrary getById(String id) {
        return titleLibraryMapper.findById(id);
    }

    public List<TitleLibrary> getAllUnmatchedTitles() {
        return titleLibraryMapper.findAllUnmatched();
    }

    public void save(TitleLibrary titleLibrary) {
        // 标题唯一性校验
        if (titleLibrary.getTitle() != null && !titleLibrary.getTitle().trim().isEmpty()) {
            TitleLibrary existing = titleLibraryMapper.findByTitle(titleLibrary.getTitle().trim());
            if (existing != null && !existing.getId().equals(titleLibrary.getId())) {
                throw new IllegalArgumentException("标题名称已存在：" + titleLibrary.getTitle().trim());
            }
        }

        if (titleLibrary.getId() == null || titleLibrary.getId().isEmpty()) {
            if (titleLibrary.getIsUsed() == null) {
                titleLibrary.setIsUsed(0);
            }
            titleLibrary.setId(UUID.randomUUID().toString().replace("-", ""));
            titleLibrary.setUseCount(0);
            titleLibraryMapper.insert(titleLibrary);
        } else {
            titleLibraryMapper.update(titleLibrary);
        }

        // 处理用户关联
        String titleId = titleLibrary.getId();
        String recommendUserId = titleLibrary.getRecommendUserId();
        if (recommendUserId != null) {
            if (!recommendUserId.isEmpty()) {
                // 有推荐用户：更新关联
                titleRecommendationMapper.deleteByTitleId(titleId);
                TitleRecommendation rec = new TitleRecommendation();
                rec.setId(UUID.randomUUID().toString().replace("-", ""));
                rec.setTitleLibraryId(titleId);
                rec.setUserId(recommendUserId);
                rec.setPlatform(titleLibrary.getPlatform());
                // track_id 使用用户订阅的赛道：优先匹配标题所属赛道，否则取用户订阅的第一个
                String titleTrackId = titleLibrary.getTrackId();
                List<UserTrack> userTracks = userTrackMapper.findByUserId(recommendUserId);
                String recTrackId = titleTrackId;
                if (userTracks != null && !userTracks.isEmpty()) {
                    recTrackId = userTracks.stream()
                            .map(UserTrack::getTrackId)
                            .filter(tid -> tid != null && tid.equals(titleTrackId))
                            .findFirst()
                            .orElse(userTracks.get(0).getTrackId());
                }
                rec.setTrackId(recTrackId);
                // 优先使用 pushDate（前端编辑时用户修改的是 pushDate），其次才回退到 recommendDate
                rec.setRecommendDate(titleLibrary.getPushDate() != null ? titleLibrary.getPushDate() : (titleLibrary.getRecommendDate() != null ? titleLibrary.getRecommendDate() : LocalDate.now()));
                titleRecommendationMapper.insert(rec);
                titleLibrary.setIsUsed(1);
                titleLibraryMapper.update(titleLibrary);
            } else {
                // 显式传入空字符串时才清空关联
                titleRecommendationMapper.deleteByTitleId(titleId);
                titleLibrary.setIsUsed(0);
                titleLibraryMapper.update(titleLibrary);
            }
        }
        // recommendUserId 为 null 时不处理关联，保持原样（防止导入等批量操作误删关联）
    }

    public void delete(String id) {
        titleLibraryMapper.delete(id);
    }

    public void updateIsUsed(String id, Integer isUsed) {
        titleLibraryMapper.updateIsUsed(id, isUsed);
    }

    public void updateAiFlavorStatus(String id, Integer aiFlavorStatus) {
        titleLibraryMapper.updateAiFlavorStatus(id, aiFlavorStatus);
    }

    public void updateIsCopied(String id, Integer isCopied) {
        titleLibraryMapper.updateIsCopied(id, isCopied);
    }

    public void updateIsConfirmed(String id, Integer isConfirmed) {
        titleLibraryMapper.updateIsConfirmed(id, isConfirmed);
    }

    public void updateConfirmStatus(String id, Integer confirmStatus) {
        titleLibraryMapper.updateConfirmStatus(id, confirmStatus);
    }

    public void updateBannedWordCheckResult(String id, String result) {
        titleLibraryMapper.updateBannedWordCheckResult(id, result);
    }

    public List<TitleLibrary> findPendingReview(String recommendDate) {
        return titleLibraryMapper.findPendingReview(recommendDate);
    }

    public List<TitleLibrary> findReviewHistory(String recommendDate) {
        return titleLibraryMapper.findReviewHistory(recommendDate);
    }

    public void updateGeneratedFile(String id, String fileUrl, String fileName) {
        titleLibraryMapper.updateGeneratedFile(id, fileUrl, fileName, LocalDateTime.now());
    }

    public void updateGenerateStatus(String id, Integer generateStatus) {
        titleLibraryMapper.updateGenerateStatus(id, generateStatus);
    }

    public void updateTitle(TitleLibrary title) {
        titleLibraryMapper.update(title);
    }

    public int batchMarkUsedForMatched() {
        return titleLibraryMapper.batchMarkUsedForMatched();
    }

    private int toIntValue(Object val) {
        if (val instanceof Number) {
            return ((Number) val).intValue();
        }
        if (val instanceof Boolean) {
            return ((Boolean) val) ? 1 : 0;
        }
        return 0;
    }

    public List<Map<String, Object>> findUnrecommendedUsers(LocalDate date, String type) {
        List<Map<String, Object>> allUsers = userMapper.findAllActiveUsers();
        List<Map<String, Object>> result = new ArrayList<>();
        List<String> unrecommendedUserIds = new ArrayList<>();

        for (Map<String, Object> user : allUsers) {
            String userId = (String) user.get("id");

            // 按类型过滤
            Integer userTypeVal = user.get("userType") != null ? ((Number) user.get("userType")).intValue() : null;
            if ("accountOpened".equals(type)) {
                if (userTypeVal == null || userTypeVal != 1) continue;
            } else if ("distributor".equals(type)) {
                if (userTypeVal == null || userTypeVal != 2) continue;
            } else if ("trial".equals(type)) {
                if (userTypeVal == null || userTypeVal != 3) continue;
            }

            // 获取用户订阅的赛道
            List<UserTrack> userTracks = userTrackMapper.findByUserId(userId);
            if (userTracks == null || userTracks.isEmpty()) {
                continue; // 没有订阅赛道，不算未推荐
            }

            // 获取用户当日已有有效推荐（有关联文章）的赛道
            List<String> recommendedTrackIds = titleRecommendationMapper.findRecommendedTrackIdsByUserAndDate(userId, date);
            Set<String> recommendedSet = new HashSet<>(recommendedTrackIds != null ? recommendedTrackIds : new ArrayList<>());

            // 检查是否所有订阅赛道都有有效推荐
            boolean allRecommended = true;
            List<String> missingTrackNames = new ArrayList<>();
            for (UserTrack ut : userTracks) {
                if (!recommendedSet.contains(ut.getTrackId())) {
                    allRecommended = false;
                    Track track = trackMapper.findById(ut.getTrackId());
                    if (track != null) {
                        missingTrackNames.add(track.getPlatforms() + "-" + track.getName());
                    }
                }
            }

            if (!allRecommended) {
                user.put("missingTracks", String.join(", ", missingTrackNames));
                result.add(user);
                unrecommendedUserIds.add(userId);
            }
        }

        // 批量查询订阅信息
        if (!unrecommendedUserIds.isEmpty()) {
            List<Track> allTracks = trackMapper.findAll();
            Map<String, Track> trackMap = allTracks.stream().collect(Collectors.toMap(Track::getId, t -> t));
            List<UserTrack> allUserTracks = userTrackMapper.findByUserIds(unrecommendedUserIds);
            Map<String, List<String>> userTrackNames = new HashMap<>();
            for (UserTrack ut : allUserTracks) {
                Track track = trackMap.get(ut.getTrackId());
                if (track != null) {
                    String label = track.getPlatforms() + "-" + track.getName();
                    userTrackNames.computeIfAbsent(ut.getUserId(), k -> new ArrayList<>()).add(label);
                }
            }
            for (Map<String, Object> user : result) {
                String userId = (String) user.get("id");
                List<String> subs = userTrackNames.getOrDefault(userId, new ArrayList<>());
                user.put("trackInfo", String.join(", ", subs));
            }
        }
        return result;
    }

    public Map<String, Object> findPushOverview(LocalDate date, String type, String keyword,
                                                 String emailPushed, String articleComplete,
                                                 int page, int pageSize) {
        List<Map<String, Object>> rows = titleLibraryMapper.findPushOverview(date);
        Map<String, Map<String, Object>> userMap = new LinkedHashMap<>();

        for (Map<String, Object> row : rows) {
            String userId = (String) row.get("userId");
            String trackId = (String) row.get("trackId");
            String trackName = (String) row.get("trackName");
            String recommendationId = (String) row.get("recommendationId");
            // postTitle 已废弃，统一使用 titleName
            String titleLibraryId = row.get("titleLibraryId") != null ? row.get("titleLibraryId").toString() : null;
            String titleName = (String) row.get("titleName");
            String generatedFileUrl = row.get("generatedFileUrl") != null ? row.get("generatedFileUrl").toString() : null;

            Map<String, Object> user = userMap.computeIfAbsent(userId, k -> {
                Map<String, Object> u = new HashMap<>();
                u.put("userId", userId);
                u.put("username", row.get("username"));
                u.put("wxName", row.get("wxName"));
                u.put("email", row.get("email"));
                u.put("userType", row.get("userType"));
                u.put("tracks", new ArrayList<Map<String, Object>>());
                u.put("totalTracks", 0);
                u.put("tracksWithPost", 0);
                u.put("tracksWithTitle", 0);
                u.put("isEmailPushed", false);
                return u;
            });

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> tracks = (List<Map<String, Object>>) user.get("tracks");
            boolean hasPost = generatedFileUrl != null && !generatedFileUrl.isEmpty();
            boolean hasTitle = titleLibraryId != null && !titleLibraryId.isEmpty();

            // 防御重复：同一赛道可能因推荐表多条记录导致重复行，去重并保留有文章的那条
            boolean found = false;
            for (Map<String, Object> existing : tracks) {
                if (trackId.equals(existing.get("trackId"))) {
                    found = true;
                    if (hasPost && !Boolean.TRUE.equals(existing.get("hasPost"))) {
                        existing.put("hasPost", true);
                        existing.put("recommendationId", recommendationId);
                        existing.put("postTitle", titleName);
                    }
                    if (hasTitle && existing.get("titleName") == null) {
                        existing.put("titleLibraryId", titleLibraryId);
                        existing.put("titleName", titleName);
                    }
                    break;
                }
            }
            if (!found) {
                Map<String, Object> track = new HashMap<>();
                track.put("trackId", trackId);
                track.put("trackName", trackName);
                track.put("hasPost", hasPost);
                track.put("recommendationId", recommendationId);
                track.put("postTitle", titleName);
                track.put("titleLibraryId", titleLibraryId);
                track.put("titleName", titleName);
                tracks.add(track);
            }

            user.put("totalTracks", tracks.size());
            user.put("tracksWithPost", tracks.stream().filter(t -> Boolean.TRUE.equals(t.get("hasPost"))).count());
            user.put("tracksWithTitle", tracks.stream().filter(t -> t.get("titleName") != null).count());
        }

        // Query email push status for each user (per title)
        for (Map<String, Object> user : userMap.values()) {
            String userId = (String) user.get("userId");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> tracks = (List<Map<String, Object>>) user.get("tracks");
            List<String> pushedTitleIds = emailPushLogMapper.findPushedTitleIdsByUserAndDate(userId, date);
            Set<String> pushedSet = new HashSet<>(pushedTitleIds != null ? pushedTitleIds : new ArrayList<>());

            int tracksPushed = 0;
            int tracksUnpushed = 0;
            for (Map<String, Object> track : tracks) {
                String tlibId = track.get("titleLibraryId") != null ? track.get("titleLibraryId").toString() : null;
                boolean hasPost = Boolean.TRUE.equals(track.get("hasPost"));
                boolean isPushed = tlibId != null && !tlibId.isEmpty() && pushedSet.contains(tlibId);
                track.put("isPushed", isPushed);
                if (hasPost) {
                    if (isPushed) {
                        tracksPushed++;
                    } else {
                        tracksUnpushed++;
                    }
                }
            }
            user.put("tracksPushed", tracksPushed);
            user.put("tracksUnpushed", tracksUnpushed);
            user.put("isEmailPushed", tracksUnpushed == 0 && tracksPushed > 0);
        }

        List<Map<String, Object>> users = new ArrayList<>(userMap.values());

        // Filter by type
        if (type != null && !type.isEmpty() && !"all".equals(type)) {
            Integer targetType = switch (type) {
                case "accountOpened" -> 1;
                case "distributor" -> 2;
                case "trial" -> 3;
                default -> null;
            };
            if (targetType != null) {
                users = users.stream().filter(u -> targetType.equals(u.get("userType"))).collect(Collectors.toList());
            }
        }

        // Filter by keyword
        if (keyword != null && !keyword.isEmpty()) {
            String kw = keyword.toLowerCase();
            users = users.stream().filter(u -> {
                String username = (String) u.get("username");
                String email = (String) u.get("email");
                return (username != null && username.toLowerCase().contains(kw))
                    || (email != null && email.toLowerCase().contains(kw));
            }).collect(Collectors.toList());
        }

        // Filter by email pushed status
        if (emailPushed != null && !emailPushed.isEmpty()) {
            boolean pushed = "1".equals(emailPushed);
            users = users.stream().filter(u -> pushed == Boolean.TRUE.equals(u.get("isEmailPushed")))
                .collect(Collectors.toList());
        }

        // Filter by article complete status
        if (articleComplete != null && !articleComplete.isEmpty()) {
            boolean complete = "1".equals(articleComplete);
            users = users.stream().filter(u -> {
                int total = ((Number) u.getOrDefault("totalTracks", 0)).intValue();
                int withPost = ((Number) u.getOrDefault("tracksWithPost", 0)).intValue();
                boolean isComplete = total > 0 && withPost == total;
                return complete == isComplete;
            }).collect(Collectors.toList());
        }

        int total = users.size();
        int offset = (page - 1) * pageSize;
        List<Map<String, Object>> pageList = new ArrayList<>();
        for (int i = offset; i < Math.min(offset + pageSize, total); i++) {
            pageList.add(users.get(i));
        }

        Map<String, Object> result = new HashMap<>();
        result.put("list", pageList);
        result.put("total", total);
        return result;
    }

    public List<Map<String, Object>> countByTrack() {
        return titleLibraryMapper.countByTrack();
    }

    public List<Map<String, Object>> findUnpushedUsers(LocalDate date) {
        List<Map<String, Object>> allUsers = userMapper.findAllActiveUsersWithEmail();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> user : allUsers) {
            String userId = (String) user.get("id");
            int count = emailPushLogMapper.countByUserAndDate(userId, date);
            if (count > 0) {
                continue;
            }
            // 查询该用户当日有关联文章的推荐
            List<Map<String, Object>> recommendations = titleRecommendationMapper.findByUserAndDate(userId, date);
            List<String> postTitles = new ArrayList<>();
            if (recommendations != null) {
                for (Map<String, Object> rec : recommendations) {
                    Object titleLibIdObj = rec.get("titleLibraryId");
                    if (titleLibIdObj == null) titleLibIdObj = rec.get("title_library_id");
                    if (titleLibIdObj == null || titleLibIdObj.toString().isEmpty()) {
                        continue;
                    }
                    TitleLibrary titleLib = titleLibraryMapper.findById(titleLibIdObj.toString());
                    if (titleLib != null && titleLib.getGeneratedFileUrl() != null && !titleLib.getGeneratedFileUrl().isEmpty()
                            && titleLib.getTitle() != null && !titleLib.getTitle().isEmpty()) {
                        postTitles.add(titleLib.getTitle());
                    }
                }
            }
            // 只返回有关联文章的用户
            if (!postTitles.isEmpty()) {
                user.put("postTitles", postTitles);
                result.add(user);
            }
        }
return result;
    }

    public void batchPushEmailForScheduled(String dateStr, List<String> userIds) {
        LocalDate pushDate = LocalDate.parse(dateStr);
        int totalUsers = userIds.size();
        int pushedUsers = 0;
        int skippedUsers = 0;
        for (String userId : userIds) {
            try {
                User user = userMapper.findById(userId);
                if (user == null) {
                    log.warn("[batchPushEmailForScheduled] 跳过: 用户不存在, userId={}", userId);
                    skippedUsers++;
                    continue;
                }
                if (user.getStatus() == null || user.getStatus() != 1) {
                    log.warn("[batchPushEmailForScheduled] 跳过: 用户已禁用, user={}, userId={}", user.getUsername(), userId);
                    skippedUsers++;
                    continue;
                }
                if (user.getEmail() == null || user.getEmail().isEmpty()) {
                    log.warn("[batchPushEmailForScheduled] 跳过: 用户未设置邮箱, user={}, userId={}", user.getUsername(), userId);
                    skippedUsers++;
                    continue;
                }
                List<Map<String, Object>> recMaps = titleRecommendationMapper.findByUserAndDate(userId, pushDate);
                if (recMaps == null || recMaps.isEmpty()) {
                    log.warn("[batchPushEmailForScheduled] 跳过: 当日没有推荐记录, user={}, userId={}", user.getUsername(), userId);
                    skippedUsers++;
                    continue;
                }
                List<String> pushedTitleIds = emailPushLogMapper.findPushedTitleIdsByUserAndDate(userId, pushDate);
                Set<String> pushedSet = new HashSet<>(pushedTitleIds != null ? pushedTitleIds : new ArrayList<>());
                boolean anyPushed = false;
                for (Map<String, Object> recMap : recMaps) {
                    String trackId = recMap.get("trackId") != null ? recMap.get("trackId").toString() : null;
                    if (trackId == null) trackId = recMap.get("track_id") != null ? recMap.get("track_id").toString() : null;
                    String titleLibId = recMap.get("titleLibraryId") != null ? recMap.get("titleLibraryId").toString() : null;
                    if (titleLibId == null) titleLibId = recMap.get("title_library_id") != null ? recMap.get("title_library_id").toString() : null;
                    if (titleLibId != null && !titleLibId.isEmpty() && pushedSet.contains(titleLibId)) {
                        log.info("[batchPushEmailForScheduled] 跳过: 已推送过, user={}, titleLibId={}", user.getUsername(), titleLibId);
                        continue;
                    }
                    TitleLibrary titleLib = this.getById(titleLibId);
                    if (titleLib == null) {
                        log.warn("[batchPushEmailForScheduled] 跳过: TitleLibrary不存在, user={}, titleLibId={}", user.getUsername(), titleLibId);
                        continue;
                    }
                    if (titleLib.getGeneratedFileUrl() == null || titleLib.getGeneratedFileUrl().isEmpty()) {
                        log.warn("[batchPushEmailForScheduled] 跳过: 尚未生成文章, user={}, title={}", user.getUsername(), titleLib.getTitle());
                        continue;
                    }
                    File articleFile = new File(titleLib.getGeneratedFileUrl().startsWith("/")
                            ? System.getProperty("user.dir") + titleLib.getGeneratedFileUrl()
                            : titleLib.getGeneratedFileUrl());
                    if (!articleFile.exists()) {
                        articleFile = new File(System.getProperty("user.dir") + File.separator + "uploads" + File.separator + "articles" + File.separator + titleLib.getGeneratedFileName());
                    }
                    if (!articleFile.exists()) {
                        log.warn("[batchPushEmailForScheduled] 跳过: 文章文件不存在, user={}, file={}, title={}", user.getUsername(), articleFile.getAbsolutePath(), titleLib.getTitle());
                        continue;
                    }
                    Track userTrack = trackMapper.findById(trackId);
                    String trackName = userTrack != null ? userTrack.getName() : "";
                    String articleTitle = titleLib.getTitle();
                    String platform = titleLib.getPlatform() != null ? titleLib.getPlatform() : "";
                    List<File> imageFiles = resolveImagePostFiles(titleLib.getImagePostUrls());
                    log.info("[batchPushEmailForScheduled] 开始发送邮件, user={}, email={}, title={}", user.getUsername(), user.getEmail(), articleTitle);
                    String cleanFileName = articleTitle.replaceAll("[^\\u4e00-\\u9fa5a-zA-Z0-9\\s]", "").trim() + ".docx";
                    if (cleanFileName.length() <= 5) {
                        cleanFileName = "文章推荐.docx";
                    }
                    emailService.sendDailyRecommendEmailWithImages(user.getEmail(), user.getUsername(), trackName, articleTitle, platform, articleFile, cleanFileName, imageFiles);
                    EmailPushLog pushLog = new EmailPushLog();
                    pushLog.setId(java.util.UUID.randomUUID().toString().replace("-", ""));
                    pushLog.setUserId(userId);
                    pushLog.setPushDate(pushDate);
                    pushLog.setType("daily_recommend");
                    pushLog.setTitleLibraryId(titleLibId);
                    emailPushLogMapper.insert(pushLog);
                    anyPushed = true;
                    log.info("[batchPushEmailForScheduled] 邮件发送成功, user={}, title={}", user.getUsername(), articleTitle);
                }
                if (anyPushed) {
                    pushedUsers++;
                } else {
                    log.warn("[batchPushEmailForScheduled] 用户无有效文章可推送, user={}, recCount={}", user.getUsername(), recMaps.size());
                    skippedUsers++;
                }
            } catch (Exception e) {
                log.error("[batchPushEmailForScheduled] 单用户推送异常, userId={}", userId, e);
                skippedUsers++;
            }
        }
        log.info("[batchPushEmailForScheduled] 批量推送完成, totalUsers={}, pushedUsers={}, skippedUsers={}", totalUsers, pushedUsers, skippedUsers);
    }

    public List<File> resolveImagePostFiles(String imagePostUrls) {
        List<File> files = new ArrayList<>();
        if (imagePostUrls == null || imagePostUrls.isEmpty()) return files;
        try {
            List<String> urls = new com.fasterxml.jackson.databind.ObjectMapper().readValue(imagePostUrls,
                    new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {});
            for (String url : urls) {
                String path = url.startsWith("/") ? System.getProperty("user.dir") + url : url;
                File f = new File(path);
                if (f.exists()) files.add(f);
            }
        } catch (Exception e) {
            // ignore
        }
        return files;
    }

    /**
     * 为指定标题生成贴图（支持指定风格，null 则随机；支持指定字体，null 则读取配置或自动选择）
     */
    public List<String> generateImagePosts(String titleLibraryId, String style, String fontFamily, String bodyFontFamily) throws Exception {
        TitleLibrary titleLib = this.getById(titleLibraryId);
        if (titleLib == null) {
            throw new RuntimeException("TitleLibrary不存在: " + titleLibraryId);
        }
        String docxPath = titleLib.getGeneratedFileUrl();
        if (docxPath == null || docxPath.isEmpty()) {
            throw new RuntimeException("文章文件不存在，请先生成文章: " + titleLibraryId);
        }
        if (docxPath.startsWith("/")) {
            docxPath = System.getProperty("user.dir") + docxPath;
        }
        if (!new File(docxPath).exists()) {
            throw new RuntimeException("文章文件不存在: " + docxPath);
        }
        log.info("[generateImagePosts] titleId={}, docxPath={}, generatedFileUrl={}", titleLibraryId, docxPath, titleLib.getGeneratedFileUrl());

        // 读取全局贴图配置
        try {
            if (style == null || style.isEmpty()) {
                Config cfgStyle = configMapper.findByKey("image_post_style");
                if (cfgStyle != null && cfgStyle.getConfigValue() != null && !cfgStyle.getConfigValue().isEmpty()) {
                    style = cfgStyle.getConfigValue().trim();
                } else {
                    // 兼容旧配置 image_post_theme
                    Config cfgTheme = configMapper.findByKey("image_post_theme");
                    if (cfgTheme != null && cfgTheme.getConfigValue() != null && !cfgTheme.getConfigValue().isEmpty()) {
                        style = cfgTheme.getConfigValue().trim();
                    }
                }
            }
            if (fontFamily == null || fontFamily.isEmpty()) {
                Config cfgFont = configMapper.findByKey("image_post_font");
                if (cfgFont != null && cfgFont.getConfigValue() != null && !cfgFont.getConfigValue().isEmpty()) {
                    fontFamily = cfgFont.getConfigValue().trim();
                }
            }
            if (bodyFontFamily == null || bodyFontFamily.isEmpty()) {
                Config cfgBodyFont = configMapper.findByKey("image_post_body_font");
                if (cfgBodyFont != null && cfgBodyFont.getConfigValue() != null && !cfgBodyFont.getConfigValue().isEmpty()) {
                    bodyFontFamily = cfgBodyFont.getConfigValue().trim();
                }
            }
        } catch (Exception e) {
            log.warn("[generateImagePosts] 读取贴图配置失败，使用默认配置", e);
        }

        // 随机风格
        if (style == null || style.isEmpty()) {
            int idx = (int) (Math.random() * CARD_STYLES.size());
            style = CARD_STYLES.get(idx);
            log.info("[generateImagePosts] 使用随机风格: {}", style);
        }
        // 若旧 theme 不在新风格列表中，兜底到小红书
        if (!CARD_STYLES.contains(style)) {
            log.warn("[generateImagePosts] 未知风格 '{}', 兜底到 xiaohongshu", style);
            style = "xiaohongshu";
        }

        String outputDir = System.getProperty("user.dir") + File.separator + "uploads" + File.separator + "image-posts" + File.separator + titleLibraryId;
        new File(outputDir).mkdirs();

        String scriptPath = resolveScriptPath("generate_image_posts.py");
        if (scriptPath == null) {
            throw new RuntimeException("贴图生成脚本不存在");
        }

        List<String> command = new ArrayList<>();
        command.add("python3");
        command.add(scriptPath);
        command.add(docxPath);
        command.add(outputDir);
        command.add(titleLibraryId);
        command.add("--style");
        command.add(style);
        command.add("--title");
        command.add(titleLib.getTitle() != null ? titleLib.getTitle() : "");
        String brandText = titleLib.getRecommendUserName() != null && !titleLib.getRecommendUserName().isEmpty()
                ? titleLib.getRecommendUserName()
                : "博主编";
        command.add("--brand-text");
        command.add(brandText);

        // 封面标签：优先使用赛道名称
        String tagText = "";
        if (titleLib.getTrackId() != null && !titleLib.getTrackId().isEmpty()) {
            Track track = trackMapper.findById(titleLib.getTrackId());
            if (track != null && track.getName() != null && !track.getName().isEmpty()) {
                tagText = track.getName();
            }
        }
        if (!tagText.isEmpty()) {
            command.add("--tag-text");
            command.add(tagText);
        }

        if (fontFamily != null && !fontFamily.isEmpty()) {
            command.add("--font-family");
            command.add(fontFamily);
        }
        if (bodyFontFamily != null && !bodyFontFamily.isEmpty()) {
            command.add("--body-font-family");
            command.add(bodyFontFamily);
        }

        // 传入字体目录绝对路径，避免 JAR 部署时 Python __file__ 为临时路径导致字体加载失败
        // 优先从 classpath 提取字体到缓存目录；回退到本地相对路径（开发环境）
        String fontDir = extractFontDir();
        command.add("--font-dir");
        command.add(fontDir);

        log.info("[generateImagePosts] 执行脚本: {}", command);
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        Process process = pb.start();

        StringBuilder stdout = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                stdout.append(line).append("\n");
            }
        }

        boolean finished = process.waitFor(120, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new RuntimeException("贴图生成超时（120秒）");
        }

        int exitCode = process.exitValue();
        if (exitCode != 0) {
            log.error("[generateImagePosts] 脚本执行失败 exitCode={} stdout={}", exitCode, stdout);
            throw new RuntimeException("贴图生成失败: " + stdout.toString().trim());
        }

        String raw = stdout.toString().trim();
        // 跳过脚本输出的诊断文本（调试信息走 stderr 而非 stdout）
        int jsonStart = raw.indexOf('{');
        int jsonEnd = raw.lastIndexOf('}');
        if (jsonStart >= 0 && jsonEnd >= jsonStart) {
            raw = raw.substring(jsonStart, jsonEnd + 1);
        }
        com.fasterxml.jackson.databind.JsonNode json = new com.fasterxml.jackson.databind.ObjectMapper().readTree(raw);
        if (!json.has("success") || !json.get("success").asBoolean()) {
            String err = json.has("error") ? json.get("error").asText() : "未知错误";
            throw new RuntimeException("贴图生成失败: " + err);
        }

        List<String> images = new ArrayList<>();
        if (json.has("images") && json.get("images").isArray()) {
            for (com.fasterxml.jackson.databind.JsonNode node : json.get("images")) {
                images.add(node.asText());
            }
        }

        // 保存到数据库
        String jsonUrls = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(images);
        titleLibraryMapper.updateImagePostUrls(titleLibraryId, jsonUrls);

        return images;
    }

    private String resolveScriptPath(String scriptName) {
        try {
            // 策略1：开发环境直接路径
            Path directPath = Paths.get(System.getProperty("user.dir"), "src", "main", "resources", "py", scriptName);
            if (Files.exists(directPath)) {
                return directPath.toString();
            }

            // 策略2：部署环境 scripts/py/ 目录（deploy.sh 上传位置）
            // user.dir 通常是 /root/app/zhiwo/admin-service，同级 ../scripts/py/ 即为部署路径
            Path deployPath = Paths.get(System.getProperty("user.dir")).getParent().resolve("scripts").resolve("py").resolve(scriptName);
            if (Files.exists(deployPath)) {
                log.info("[resolveScriptPath] 使用部署环境脚本: {}", deployPath);
                return deployPath.toString();
            }

            // 策略3：从 JAR 包中提取
            try (InputStream is = getClass().getClassLoader().getResourceAsStream("py/" + scriptName)) {
                if (is != null) {
                    Path tempScript = Files.createTempFile(scriptName.replace(".py", ""), ".py");
                    Files.copy(is, tempScript, StandardCopyOption.REPLACE_EXISTING);
                    tempScript.toFile().deleteOnExit();
                    log.info("[resolveScriptPath] 从 JAR 提取临时脚本: {}", tempScript);
                    return tempScript.toString();
                }
            }
        } catch (Exception e) {
            log.error("[resolveScriptPath] 无法解析脚本路径: {}", scriptName, e);
        }
        return null;
    }

    /**
     * 获取字体目录的绝对路径。
     * 策略：
     * 1. 优先使用服务器上的字体目录（部署环境，由 deploy.sh 同步）
     * 2. 回退到本地相对路径（开发环境）
     * 不再从 classpath/JAR 内提取字体，避免字体文件过大导致 JAR 膨胀，
     * 且避免 woff/woff2 等 Web 格式在 PIL 中渲染质量差的问题。
     */
    private String extractFontDir() {
        // 策略1：服务器部署路径（deploy.sh 会把 admin-frontend/src/assets/font 同步到这里）
        String serverFontPath = "/root/app/zhiwo/admin-frontend/src/assets/font";
        if (java.nio.file.Files.exists(java.nio.file.Paths.get(serverFontPath))) {
            log.info("[extractFontDir] 使用服务器字体路径: {}", serverFontPath);
            return serverFontPath;
        }

        // 策略2：本地开发路径
        String localPath = java.nio.file.Paths.get(System.getProperty("user.dir"))
                .getParent()
                .resolve("admin-frontend/src/assets/font")
                .toAbsolutePath()
                .normalize()
                .toString();
        if (java.nio.file.Files.exists(java.nio.file.Paths.get(localPath))) {
            log.info("[extractFontDir] 使用本地字体路径: {}", localPath);
            return localPath;
        }

        log.warn("[extractFontDir] 未找到字体目录，回退到临时目录: {}", localPath);
        return localPath;
    }
}
