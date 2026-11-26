package com.pathpilot.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "Full name is required.")
        @Size(min = 2, max = 150, message = "Full name must be between 2 and 150 characters.")
        String fullName,

        @NotBlank(message = "Phone number is required.")
        @Pattern(regexp = "^\\d{10,15}$", message = "Phone number must be 10 to 15 digits.")
        String phone,

        @NotBlank(message = "Email address is required.")
        @Email(message = "Enter a valid email address.")
        @Size(max = 255, message = "Email address is too long.")
        String email,

        @NotBlank(message = "Password is required.")
        @Pattern(
                regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,100}$",
                message = "Password must be at least 8 characters and include at least one letter and one number."
        )
        String password,

        @NotBlank(message = "Please confirm your password.")
        String confirmPassword
) {
    @Override
    public String toString() {
        return "RegisterRequest[fullName=" + fullName + ", phone=" + phone + ", email=" + email + ", password=[REDACTED]]";
    }
}
