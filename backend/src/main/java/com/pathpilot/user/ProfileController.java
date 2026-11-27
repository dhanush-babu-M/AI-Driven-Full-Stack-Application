package com.pathpilot.user;

import com.pathpilot.security.CurrentUser;
import com.pathpilot.user.dto.ProfileDtos.ProfileLinksResponse;
import com.pathpilot.user.dto.ProfileDtos.ProfileSettingsResponse;
import com.pathpilot.user.dto.ProfileDtos.UpdateProfileLinksRequest;
import com.pathpilot.user.dto.ProfileDtos.UpdateProfileSettingsRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/profile")
public class ProfileController {

    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping
    public ProfileSettingsResponse getProfile() {
        return profileService.getProfileSettings(CurrentUser.id());
    }

    @PutMapping
    public ProfileSettingsResponse updateProfile(@Valid @RequestBody UpdateProfileSettingsRequest request) {
        return profileService.updateProfileSettings(CurrentUser.id(), request);
    }

    @GetMapping("/links")
    public ProfileLinksResponse getLinks() {
        return profileService.getLinks(CurrentUser.id());
    }

    @PutMapping("/links")
    public ProfileLinksResponse updateLinks(@Valid @RequestBody UpdateProfileLinksRequest request) {
        return profileService.updateLinks(CurrentUser.id(), request);
    }
}
