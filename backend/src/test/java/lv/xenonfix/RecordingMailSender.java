package lv.xenonfix;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/** Keeps the emails instead of sending them, or fails like an unreachable mail server. */
public class RecordingMailSender extends JavaMailSenderImpl {

	private final List<Email> sent = new CopyOnWriteArrayList<>();

	private volatile boolean failing;

	public void setFailing(boolean failing) {
		this.failing = failing;
	}

	public List<Email> sentWith(String subjectPart) {
		return this.sent.stream().filter((email) -> email.subject().contains(subjectPart)).toList();
	}

	@Override
	protected void doSend(MimeMessage[] messages, Object[] originalMessages) {
		if (this.failing) {
			throw new MailSendException("Mail server not reachable");
		}
		try {
			for (MimeMessage message : messages) {
				var replyTo = message.getReplyTo();
				this.sent.add(new Email(message.getRecipients(Message.RecipientType.TO)[0].toString(),
						((InternetAddress) message.getFrom()[0]).getAddress(),
						(replyTo != null) ? replyTo[0].toString() : null, message.getSubject(),
						(String) message.getContent()));
			}
		}
		catch (MessagingException | java.io.IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

	public record Email(String to, String from, String replyTo, String subject, String body) {
	}

}
