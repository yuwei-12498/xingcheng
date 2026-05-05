package com.citytrip.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.citytrip.annotation.AdminRequired;
import com.citytrip.common.AuthConstants;
import com.citytrip.model.entity.Poi;
import com.citytrip.model.vo.AdminCommunityPostVO;
import com.citytrip.model.vo.AdminUserVO;
import com.citytrip.service.AdminService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@AdminRequired
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/users")
    public Page<AdminUserVO> getUserPage(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String username) {
        return adminService.getUserPage(page, size, username);
    }

    @PatchMapping("/users/{userId}/status")
    public void updateUserStatus(@PathVariable Long userId, @RequestParam Integer status) {
        adminService.updateUserStatus(userId, status);
    }

    @GetMapping("/pois")
    public Page<Poi> getPoiPage(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String name) {
        return adminService.getPoiPage(page, size, name);
    }

    @PostMapping("/pois")
    @ResponseStatus(HttpStatus.CREATED)
    public Poi createPoi(@RequestBody Poi poi) {
        return adminService.createPoi(poi);
    }

    @PutMapping("/pois")
    public void updatePoi(@RequestBody Poi poi) {
        adminService.updatePoi(poi);
    }

    @DeleteMapping("/pois/{poiId}")
    public void deletePoi(@PathVariable Long poiId) {
        adminService.deletePoi(poiId);
    }

    @PatchMapping("/pois/{poiId}/status")
    public void updatePoiTemporaryStatus(@PathVariable Long poiId,
                                         @RequestParam Integer temporarilyClosed,
                                         @RequestParam(required = false) String statusNote) {
        adminService.updatePoiTemporaryStatus(poiId, temporarilyClosed, statusNote);
    }

    @GetMapping("/community/posts")
    public Page<AdminCommunityPostVO> getCommunityPostPage(@RequestParam(defaultValue = "1") int page,
                                                           @RequestParam(defaultValue = "10") int size,
                                                           @RequestParam(required = false) String keyword,
                                                           @RequestParam(required = false) Integer pinned,
                                                           @RequestParam(required = false) Integer deleted) {
        return adminService.getCommunityPostPage(page, size, keyword, pinned, deleted);
    }

    @PatchMapping("/community/posts/{id}/pin")
    public void updateCommunityPostPin(@PathVariable Long id,
                                       @RequestParam boolean pinned,
                                       HttpServletRequest request) {
        adminService.updateCommunityPostPin(currentUserId(request), id, pinned);
    }

    @DeleteMapping("/community/posts/{id}")
    public void deleteCommunityPost(@PathVariable Long id, HttpServletRequest request) {
        adminService.deleteCommunityPost(currentUserId(request), id);
    }

    private Long currentUserId(HttpServletRequest request) {
        return request == null ? null : (Long) request.getAttribute(AuthConstants.LOGIN_USER_ID);
    }
}