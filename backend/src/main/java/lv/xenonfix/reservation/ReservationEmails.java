package lv.xenonfix.reservation;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

import lv.xenonfix.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Tells the customer and the shop about a new reservation.
 *
 * <p>
 * Runs only after the reservation is saved, and in the background, so a mail server problem
 * can neither lose a reservation nor keep the customer waiting. A failed email is not sent
 * again; the log has the reference, and never the customer's details.
 */
@Component
class ReservationEmails {

	private static final Logger logger = LoggerFactory.getLogger(ReservationEmails.class);

	private final JavaMailSender mailSender;

	private final AppProperties.Mail settings;

	ReservationEmails(JavaMailSender mailSender, AppProperties properties) {
		this.mailSender = mailSender;
		this.settings = properties.mail();
	}

	@Async
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	void onReservationCreated(ReservationCreated reservation) {
		sendToShop(reservation);
		sendToCustomer(reservation);
	}

	@Async
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	void onStatusChanged(ReservationStatusChanged change) {
		send(change.reference(), "status", (message) -> {
			message.setTo(change.customerEmail());
			message.setSubject(StatusTexts.subject(change));
			message.setText(StatusTexts.body(change), false);
		});
	}

	private void sendToCustomer(ReservationCreated reservation) {
		send(reservation.reference(), "customer", (message) -> {
			message.setTo(reservation.customerEmail());
			message.setSubject(CustomerTexts.subject(reservation));
			message.setText(CustomerTexts.body(reservation), false);
		});
	}

	private void sendToShop(ReservationCreated reservation) {
		send(reservation.reference(), "shop", (message) -> {
			message.setTo(this.settings.shop());
			// So that answering the email goes to the customer
			message.setReplyTo(reservation.customerEmail());
			message.setSubject("Jauna rezervācija " + reservation.reference());
			message.setText(shopBody(reservation), false);
		});
	}

	private void send(String reference, String recipient, MessageContent content) {
		try {
			MimeMessage mimeMessage = this.mailSender.createMimeMessage();
			MimeMessageHelper message = new MimeMessageHelper(mimeMessage, StandardCharsets.UTF_8.name());
			message.setFrom(this.settings.from());
			content.fill(message);
			this.mailSender.send(mimeMessage);
		}
		catch (MessagingException | MailException ex) {
			// The exception text can hold addresses, so only its type goes to the log
			logger.error("Could not send the {} email of reservation {}: {}", recipient, reference,
					ex.getClass().getSimpleName());
		}
	}

	private static String shopBody(ReservationCreated reservation) {
		return """
				Jauna rezervācija: %s

				Prece: %s
				%sDaudzums: %d
				Cena par vienību: %s
				Kopā: %s

				Klients: %s
				Telefons: %s
				E-pasts: %s
				Valoda: %s
				%s""".formatted(reservation.reference(), reservation.latvianProductName(),
				line("Detaļas numurs", reservation.partNumber()), reservation.quantity(), money(reservation.unitPrice()),
				money(reservation.total()), reservation.customerName(), reservation.customerPhone(),
				reservation.customerEmail(), reservation.language(), line("Komentārs", reservation.comment()));
	}

	private static String line(String label, String value) {
		return (value != null) ? label + ": " + value + "\n" : "";
	}

	private static String money(BigDecimal amount) {
		return String.format(Locale.ROOT, "%.2f EUR", amount);
	}

	@FunctionalInterface
	private interface MessageContent {

		void fill(MimeMessageHelper message) throws MessagingException;

	}

	/** When an admin confirms or cancels. Admins' notes are not available here, on purpose. */
	private static final class StatusTexts {

		private static final String CONFIRMED_LV = """
				Labdien, %1$s!

				Jūsu rezervācija %2$s ir apstiprināta.

				Prece: %3$s
				Daudzums: %4$d

				Preci varat saņemt mūsu darbnīcā: Bauskas iela 16c, Rīga.

				Jautājumi? Zvaniet +371 288 03 119 vai rakstiet info@xenonfix.lv.

				XenonFix (SIA Elekanga)
				""";

		private static final String CONFIRMED_RU = """
				Здравствуйте, %1$s!

				Ваше резервирование %2$s подтверждено.

				Товар: %3$s
				Количество: %4$d

				Забрать товар можно в нашей мастерской: Bauskas iela 16c, Рига.

				Вопросы? Звоните +371 288 03 119 или пишите info@xenonfix.lv.

				XenonFix (SIA Elekanga)
				""";

		private static final String CONFIRMED_EN = """
				Hello %1$s,

				Your reservation %2$s is confirmed.

				Product: %3$s
				Quantity: %4$d

				You can collect the product from our workshop: Bauskas iela 16c, Riga.

				Questions? Call +371 288 03 119 or write to info@xenonfix.lv.

				XenonFix (SIA Elekanga)
				""";

		private static final String CANCELLED_LV = """
				Labdien, %1$s!

				Jūsu rezervācija %2$s ir atcelta.

				Prece: %3$s
				Daudzums: %4$d

				Ja Jums ir jautājumi, zvaniet +371 288 03 119 vai rakstiet info@xenonfix.lv.

				XenonFix (SIA Elekanga)
				""";

		private static final String CANCELLED_RU = """
				Здравствуйте, %1$s!

				Ваше резервирование %2$s отменено.

				Товар: %3$s
				Количество: %4$d

				Если у вас есть вопросы, звоните +371 288 03 119 или пишите info@xenonfix.lv.

				XenonFix (SIA Elekanga)
				""";

		private static final String CANCELLED_EN = """
				Hello %1$s,

				Your reservation %2$s has been cancelled.

				Product: %3$s
				Quantity: %4$d

				If you have any questions, call +371 288 03 119 or write to info@xenonfix.lv.

				XenonFix (SIA Elekanga)
				""";

		private StatusTexts() {
		}

		static String subject(ReservationStatusChanged change) {
			boolean confirmed = change.status() == ReservationStatus.CONFIRMED;
			return switch (change.language()) {
				case lv -> "Rezervācija " + change.reference() + (confirmed ? " apstiprināta" : " atcelta") + " — XenonFix";
				case ru -> "Резервирование " + change.reference() + (confirmed ? " подтверждено" : " отменено") + " — XenonFix";
				case en -> "Reservation " + change.reference() + (confirmed ? " confirmed" : " cancelled") + " — XenonFix";
			};
		}

		static String body(ReservationStatusChanged change) {
			boolean confirmed = change.status() == ReservationStatus.CONFIRMED;
			String template = switch (change.language()) {
				case lv -> confirmed ? CONFIRMED_LV : CANCELLED_LV;
				case ru -> confirmed ? CONFIRMED_RU : CANCELLED_RU;
				case en -> confirmed ? CONFIRMED_EN : CANCELLED_EN;
			};
			return template.formatted(change.customerName(), change.reference(), change.productName(),
					change.quantity());
		}

	}

	private static final class CustomerTexts {

		private static final String LV = """
				Labdien, %1$s!

				Esam saņēmuši Jūsu rezervāciju %2$s.

				Prece: %3$s
				%4$sDaudzums: %5$d
				Kopā: %6$s

				Tagad nekas nav jāmaksā. Mēs ar Jums sazināsimies, lai apstiprinātu rezervāciju. Preci varēs saņemt mūsu darbnīcā: Bauskas iela 16c, Rīga.

				Jautājumi? Zvaniet +371 288 03 119 vai rakstiet info@xenonfix.lv.

				XenonFix (SIA Elekanga)
				""";

		private static final String RU = """
				Здравствуйте, %1$s!

				Мы получили ваше резервирование %2$s.

				Товар: %3$s
				%4$sКоличество: %5$d
				Итого: %6$s

				Сейчас платить ничего не нужно. Мы свяжемся с вами, чтобы подтвердить резервирование. Забрать товар можно будет в нашей мастерской: Bauskas iela 16c, Рига.

				Вопросы? Звоните +371 288 03 119 или пишите info@xenonfix.lv.

				XenonFix (SIA Elekanga)
				""";

		private static final String EN = """
				Hello %1$s,

				We have received your reservation %2$s.

				Product: %3$s
				%4$sQuantity: %5$d
				Total: %6$s

				There is nothing to pay now. We will contact you to confirm the reservation. You can collect the product from our workshop: Bauskas iela 16c, Riga.

				Questions? Call +371 288 03 119 or write to info@xenonfix.lv.

				XenonFix (SIA Elekanga)
				""";

		private CustomerTexts() {
		}

		static String subject(ReservationCreated reservation) {
			return switch (reservation.language()) {
				case lv -> "Jūsu rezervācija " + reservation.reference() + " — XenonFix";
				case ru -> "Ваше резервирование " + reservation.reference() + " — XenonFix";
				case en -> "Your reservation " + reservation.reference() + " — XenonFix";
			};
		}

		static String body(ReservationCreated reservation) {
			String partNumberLabel = switch (reservation.language()) {
				case lv -> "Detaļas numurs";
				case ru -> "Номер детали";
				case en -> "Part number";
			};
			String template = switch (reservation.language()) {
				case lv -> LV;
				case ru -> RU;
				case en -> EN;
			};
			return template.formatted(reservation.customerName(), reservation.reference(), reservation.productName(),
					line(partNumberLabel, reservation.partNumber()), reservation.quantity(), money(reservation.total()));
		}

	}

}
