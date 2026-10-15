package com.example.fridiet.service;

import com.example.fridiet.entity.User;
import com.example.fridiet.repository.UserRepository;
import com.example.fridiet.util.RedisUtil;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailAuthService {

    private final JavaMailSender javaMailSender;
    private final UserRepository userRepository;
    private final RedisUtil redisUtil;

    private static final String CODE_PREFIX = "EMAIL_CODE:";
    private static final String VERIFIED_PREFIX = "EMAIL_VERIFIED:";
    private static final long CODE_EXPIRE_SECONDS = 180L; // 3분(인증 코드 유효 시간)
    private static final long VERIFIED_EXPIRE_SECONDS = 1800L; //30분(인증 후 가입 대기 유효 시간)

    /**
     * 회원가입용 코드 전송
     */
    public void sendSignupCode(String email) {
        userRepository.findByEmail(email).ifPresent(user -> {
            if(user.getSocialProvider().equals("KAKAO")) {
                throw new IllegalArgumentException("카카오 소셜 로그인으로 가입된 계정입니다. 카카오 로그인을 이용해주세요.");
            } else if(user.getSocialProvider().equals("GOOGLE")) {
                throw new IllegalArgumentException("구글 소셜 로그인으로 가입된 계정입니다. 구글 로그인을 이용해주세요.");
            } else {
                throw new IllegalArgumentException("이미 가입된 이메일입니다. 로그인해주세요.");
            }
        });

        sendVerificationMail(email, "회원가입");
    }

    /**
     * 비밀번호 변경용 코드 전송
     */
    public void sendPasswordResetCode(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("가입되지 않은 이메일입니다."));

        if ("KAKAO".equals(user.getSocialProvider())) {
            throw new IllegalArgumentException("카카오 로그인 계정입니다. 카카오로 로그인해주세요.");
        } else if ("GOOGLE".equals(user.getSocialProvider())) {
            throw new IllegalArgumentException("구글 로그인 계정입니다. 구글로 로그인해주세요.");
        }

        sendVerificationMail(email, "비밀번호 재설정");
    }

    /**
     * 6자리 인증 코드 생성, Redis 저장 및 이메일 전송
     */
    public void sendVerificationMail(String email, String purpose) {

        // 6자리 난수 코드 생성
        String code = generateRandomCode();

        // Reids에 저장
        redisUtil.setDataExpire(CODE_PREFIX + email, code, CODE_EXPIRE_SECONDS);

        // 메일 발송, purpose에 따라 안내 문구 변경
        sendMail(email, code, purpose);
    }

    /**
     * Redis에 저장된 인증 코드와 사용자가 입력한 코드 검증
     */
    public boolean verifyCode(String email, String inputCode) {
        String savedCode = redisUtil.getData(CODE_PREFIX + email);

        if(savedCode == null) {
            throw new IllegalArgumentException("인증시간이 만료되었거나 요청 이력이 없습니다. 다시 요청해주세요.");
        }

        if(!savedCode.equals(inputCode)) {
            throw new IllegalArgumentException("인증 코드가 일치하지 않습니다.");
        }

        redisUtil.deleteData(CODE_PREFIX + email);
        redisUtil.setDataExpire(VERIFIED_PREFIX + email, "VERIFIED", VERIFIED_EXPIRE_SECONDS);

        return true;
    }

    /**
     * 이메일 인증을 통과하였는지 확인
     */
    public void checkEmailIsVerified(String email) {
        String isVerified = redisUtil.getData(VERIFIED_PREFIX + email);
        if(isVerified == null || !"VERIFIED".equals(isVerified)) {
            throw new IllegalArgumentException("이메일 인증이 완료되지 않았습니다.");
        }
    }

    /**
     * 회원가입 완료 후 Redis의 인증 완료 키 제거
     */
    public void removeVerifiedStatus(String email) {
        redisUtil.deleteData(VERIFIED_PREFIX + email);
    }

    /**
     * 이메일 발송 HTML
     */
    private void sendMail(String toEmail, String code, String purpose) {
        try {
            MimeMessage message = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(toEmail);
            if("회원가입".equals(purpose)) {
                helper.setSubject("[Fridiet] 회원가입 이메일 인증 번호 안내");

                String htmlContent = """
                <div style="font-family: Arial, sans-serif; padding: 20px; max-width: 500px; margin: 0 auto; border: 1px solid #e0e0e0; border-radius: 8px;">
                    <h2 style="color: #007aff; margin-bottom: 10px;">Fridiet 회원가입 인증</h2>
                    <p style="color: #333; font-size: 14px;">Fridiet에 오신 것을 환영합니다.<br>아래의 6자리 인증 번호를 입력하여 회원가입을 완료해 주세요.</p>
                    <div style="background: #f4f6f8; padding: 15px; text-align: center; border-radius: 6px; margin: 20px 0;">
                        <span style="font-size: 26px; font-weight: bold; letter-spacing: 5px; color: #1c1c1e;">%s</span>
                    </div>
                    <p style="color: #888; font-size: 12px;">* 인증 번호는 발송 시점으로부터 <b>3분간 유효</b>합니다.</p>
                </div>
            """.formatted(code);

                helper.setText(htmlContent, true);
            } else if("비밀번호 재설정".equals(purpose)) {
                helper.setSubject("[Fridiet] 비밀번호 재설정 이메일 인증 번호 안내");

                String htmlContent = """
                <div style="font-family: Arial, sans-serif; padding: 20px; max-width: 500px; margin: 0 auto; border: 1px solid #e0e0e0; border-radius: 8px;">
                    <h2 style="color: #007aff; margin-bottom: 10px;">Fridiet 비밀번호 재설정 인증</h2>
                    <p style="color: #333; font-size: 14px;">비밀번호 변경을 위해 아래 6자리 인증 번호를 입력해 주세요.</p>
                    <div style="background: #f4f6f8; padding: 15px; text-align: center; border-radius: 6px; margin: 20px 0;">
                        <span style="font-size: 26px; font-weight: bold; letter-spacing: 5px; color: #1c1c1e;">%s</span>
                    </div>
                    <p style="color: #888; font-size: 12px;">* 인증 번호는 발송 시점으로부터 <b>3분간 유효</b>합니다.</p>
                </div>
            """.formatted(code);

                helper.setText(htmlContent, true);
            }

            javaMailSender.send(message);
            log.info("이메일 인증 코드 발송 성공: {}", toEmail);
        } catch(MessagingException e) {
            log.error("메일 발송 실패: {}", e.getMessage());
            throw new RuntimeException("이메일 발송에 실패했습니다. 다시 시도해주세요.");
        }
    }

    private String generateRandomCode() {
        SecureRandom random = new SecureRandom();
        int num = random.nextInt(900000) + 100000;
        return String.valueOf(num);
    }
}
