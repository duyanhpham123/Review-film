package duyanh.film.service;

import org.springframework.core.task.TaskExecutor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import org.springframework.beans.factory.annotation.Qualifier;

import duyanh.film.config.AppProperties;
import lombok.extern.slf4j.Slf4j;

/**
 * Gửi mail ở luồng nền và chỉ sau khi transaction commit, để không gửi link chứa token
 * của một giao dịch đã bị rollback. Gửi lỗi thì ghi log, không làm hỏng request.
 */
@Slf4j
@Service
public class MailService {

	private final JavaMailSender mailSender;
	private final TaskExecutor taskExecutor;
	private final AppProperties props;

	public MailService(JavaMailSender mailSender,
			@Qualifier("applicationTaskExecutor") TaskExecutor taskExecutor, AppProperties props) {
		this.mailSender = mailSender;
		this.taskExecutor = taskExecutor;
		this.props = props;
	}

	public void sendVerifyEmail(String to, String username, String token) {
		String link = props.fe().baseUrl() + "/verify-email?token=" + token;
		send(to, "Xác thực email tài khoản Review Film",
				"Chào " + username + ",\n\nBấm vào link sau để xác thực email (hết hạn sau 24 giờ):\n" + link
						+ "\n\nNếu bạn không đăng ký, hãy bỏ qua email này.");
	}

	public void sendResetPassword(String to, String username, String token) {
		String link = props.fe().baseUrl() + "/reset-password?token=" + token;
		send(to, "Đặt lại mật khẩu Review Film",
				"Chào " + username + ",\n\nBấm vào link sau để đặt lại mật khẩu (hết hạn sau 30 phút):\n" + link
						+ "\n\nNếu bạn không yêu cầu, hãy bỏ qua email này.");
	}

	private void send(String to, String subject, String text) {
		Runnable task = () -> {
			try {
				SimpleMailMessage msg = new SimpleMailMessage();
				msg.setFrom(props.mail().from());
				msg.setTo(to);
				msg.setSubject(subject);
				msg.setText(text);
				mailSender.send(msg);
			} catch (Exception e) {
				log.warn("Gửi mail tới {} thất bại: {}", to, e.getMessage());
			}
		};
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					taskExecutor.execute(task);
				}
			});
		} else {
			taskExecutor.execute(task);
		}
	}
}
