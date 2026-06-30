package com.feilonglab.smtp.mq;

import java.io.UnsupportedEncodingException;
import java.util.Properties;
import java.util.ResourceBundle;

import javax.mail.Authenticator;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.PropertySource;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import com.feilonglab.smtp.unified.MailRequest;
import com.feilonglab.springboot.enums.SendDebugFlag;
import com.feilonglab.springboot.enums.SmtpDebugFlag;
import com.feilonglab.springboot.util.MessageUtils;

/**
 * SMTP 邮件发送客户端封装。
 * <p>
 * 提供连接的打开、邮件发送、自动关闭连接等功能。
 * </p>
 * <p>
 * <strong>属性配置加载设计说明：</strong><br>
 * 1. <b>Java 原生读取方式：</b>在默认无参构造函数中，使用
 * {@link java.util.ResourceBundle#getBundle(String)} 直接从类路径加载
 * {@code mail.properties} 文件。这是一种标准 Java 的读取方式，在不依赖 Spring 容器的普通 Java
 * 环境下依然能够良好运行。<br>
 * 2. <b>Spring Boot 读取方式：</b>当作为 Spring Bean 注入时，利用
 * {@link org.springframework.context.annotation.PropertySource} 显式指定类路径下的
 * {@code mail.properties} 资源，使其属性合并至 Spring {@code Environment} 中，供后续其它 Spring
 * 组件查询或 {@code @Value} 注入。<br>
 * 3. <b>管理模式：</b>配置了 {@code @Component} 与 {@code @Scope("prototype")}，允许使用
 * {@code ObjectFactory<SmtpClient>} 获取具备独立生命周期和 TCP 连接的客户端实例，并确保在
 * try-with-resources 块中自动关闭。
 * </p>
 *
 * @author feilonglab
 * @version 0.1.1
 */
@Component
@Scope("prototype")
@PropertySource("classpath:mail.properties")
public class SmtpClient implements AutoCloseable {

	/** 日志记录器 */
	private static final Logger logger = LoggerFactory.getLogger(SmtpClient.class);

	/** 邮件服务器会话对象，用于存储连接配置 */
	private Session session;
	/** 邮件传输对象，用于管理底层的 TCP 连接 and 数据发送 */
	private Transport transport;

	/** SMTP 服务器的主机名或 IP 地址 */
	private String host;

	/** SMTP 服务器端口，默认 587 (常用于 STARTTLS) */
	private int port = 587;

	/** 认证用户名 */
	private String username;

	/** 认证密码或应用授权码 */
	private String password;

	/** 是否启用 STARTTLS 安全升级，默认 true */
	private boolean useTls = true;

	/** 是否直接启用 SSL 安全加密连接，默认 false */
	private boolean useSsl = false;

	/** 发件人名称（展示给接收方的别名） */
	private String senderName;

	/** 发件人邮箱地址 */
	private String senderEmail;

	/** 调试拦截标志: "0" 表示开启拦截（模拟连接与发送），"1" 表示真实发送 */
	private String debugFlag = SmtpDebugFlag.REAL_CONNECTION.getValue();

	/** 连接超时配置，单位：毫秒 (默认 5000) */
	private String connectionTimeoutMs = "5000";

	/** 读取/读取响应超时配置，单位：毫秒 (默认 5000) */
	private String timeoutMs = "5000";

	/** 写入超时配置，单位：毫秒 (默认 5000) */
	private String writeTimeoutMs = "5000";

	/** 调试模式标记，控制是否输出 javax.mail 协议层详细通信日志 */
	private boolean debug = false;

	/** 发送结果输出标志: "0" 表示关闭（不输出错误信息），"1" 表示开启（会输出错误信息） */
	private String sendDebugFlag = SendDebugFlag.SEND_SUCCESS.getValue();

	/**
	 * 默认构造函数。 在服务启动或创建实例时使用 ResourceBundle 加载配置文件。
	 */
	public SmtpClient() {
		try {
			ResourceBundle bundle = ResourceBundle.getBundle("mail");
			this.host = getValue(bundle, "mail.smtp.host", null);
			String portStr = getValue(bundle, "mail.smtp.port", "587");
			this.port = Integer.parseInt(portStr);
			this.username = getValue(bundle, "mail.smtp.username", null);
			this.password = getValue(bundle, "mail.smtp.password", null);
			this.useTls = Boolean.parseBoolean(getValue(bundle, "mail.smtp.starttls.enable", "true"));
			this.useSsl = Boolean.parseBoolean(getValue(bundle, "mail.smtp.ssl.enable", "false"));
			this.senderName = getValue(bundle, "mail.smtp.sender.name", null);
			this.senderEmail = getValue(bundle, "mail.smtp.sender.email", null);
			this.debugFlag = getValue(bundle, "mail.smtp.debug.flag", SmtpDebugFlag.REAL_CONNECTION.getValue());
			this.connectionTimeoutMs = getValue(bundle, "mail.smtp.connectiontimeout", "5000");
			this.timeoutMs = getValue(bundle, "mail.smtp.timeout", "5000");
			this.writeTimeoutMs = getValue(bundle, "mail.smtp.writetimeout", "5000");
			this.debug = Boolean.parseBoolean(getValue(bundle, "mail.debug", "false"));
			this.sendDebugFlag = getValue(bundle, "mail.smtp.send.debug.flag", SendDebugFlag.SEND_SUCCESS.getValue());
		} catch (Exception e) {
			logger.warn("Failed to load mail configuration via ResourceBundle: {}", e.getMessage());
		}
	}

	private String getValue(ResourceBundle bundle, String key, String defaultValue) {
		return bundle.containsKey(key) ? bundle.getString(key) : defaultValue;
	}

	/**
	 * 直接使用指定连接参数构造 SMTP 客户端。
	 *
	 * @param host     SMTP 服务器主机名 (Host)
	 * @param port     SMTP 服务端口 (Port)
	 * @param username 邮箱账号用户名
	 * @param password 邮箱账号密码/授权码
	 * @param useTls   是否开启 STARTTLS
	 * @param useSsl   是否开启 SSL
	 */
	public SmtpClient(String host, int port, String username, String password, boolean useTls, boolean useSsl) {
		this.host = host;
		this.port = port;
		this.username = username;
		this.password = password;
		this.useTls = useTls;
		this.useSsl = useSsl;
	}

	/**
	 * 建立与 SMTP 服务器的连接，并初始化 Session 与 Transport。
	 *
	 * @throws MessagingException    连接失败或握手异常时抛出
	 * @throws IllegalStateException 如果 SMTP 主机地址未配置
	 */
	public synchronized void open() throws MessagingException {
		// 拦截模式检查：不进行真实网络连接
		if (SmtpDebugFlag.SIMULATION.getValue().equals(debugFlag)) {
			logger.info(MessageUtils.getMessage("smtp.conn.simulation.open", host, port));
			// 即使在拦截模式下也初始化默认 Session，防止 MimeMessage 传入 null
			Properties sessionProps = new Properties();
			sessionProps.put("mail.smtp.host", host != null ? host : "localhost");
			sessionProps.put("mail.smtp.port", String.valueOf(port));
			this.session = Session.getInstance(sessionProps);
			return;
		}

		// 真实连接模式下，必须配置主机地址
		if (host == null || host.isEmpty()) {
			throw new IllegalStateException(MessageUtils.getMessage("smtp.error.host.missing"));
		}

		// 避免重复连接
		if (this.transport != null && this.transport.isConnected()) {
			logger.info(MessageUtils.getMessage("smtp.conn.already.established", host, port));
			return;
		}

		logger.info(MessageUtils.getMessage("smtp.conn.initializing", host, port, useTls, useSsl));

		// 配置邮件会话属性
		Properties sessionProps = new Properties();
		sessionProps.put("mail.smtp.host", host);
		sessionProps.put("mail.smtp.port", String.valueOf(port));

		// 只有在提供用户名时才启用 SMTP 认证
		boolean hasAuth = username != null && !username.isEmpty();
		sessionProps.put("mail.smtp.auth", String.valueOf(hasAuth));

		sessionProps.put("mail.smtp.starttls.enable", String.valueOf(useTls));
		sessionProps.put("mail.smtp.ssl.enable", String.valueOf(useSsl));

		// 设置连接超时、Socket 读取和写入超时，防止连接无限挂起
		sessionProps.put("mail.smtp.connectiontimeout", connectionTimeoutMs);
		sessionProps.put("mail.smtp.timeout", timeoutMs);
		sessionProps.put("mail.smtp.writetimeout", writeTimeoutMs);

		// 配置 SSL Socket 工厂参数以支持 SSL 连接
		if (useSsl) {
			sessionProps.put("mail.smtp.socketFactory.port", String.valueOf(port));
			sessionProps.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
			sessionProps.put("mail.smtp.socketFactory.fallback", "false");
		}

		// 创建认证器对象，如果需要认证则提供用户名和密码
		Authenticator authenticator = null;
		if (hasAuth) {
			authenticator = new Authenticator() {
				@Override
				protected PasswordAuthentication getPasswordAuthentication() {
					return new PasswordAuthentication(username, password);
				}
			};
		}

		// 创建邮件会话对象，并设置调试模式
		this.session = Session.getInstance(sessionProps, authenticator);
		this.session.setDebug(this.debug);
		this.transport = session.getTransport("smtp");

		// 建立与 SMTP 服务器的连接
		if (hasAuth) {
			this.transport.connect(host, port, username, password);
		} else {
			this.transport.connect();
		}

		logger.info(MessageUtils.getMessage("smtp.conn.success", host, port));
	}

	/**
	 * 发送邮件。
	 *
	 * @param recipientName  收件人姓名
	 * @param recipientEmail 收件人邮箱地址
	 * @param subject        邮件主题
	 * @param content        邮件正文内容（支持 HTML 格式）
	 * @throws MessagingException           构建邮件对象或发送邮件失败时抛出
	 * @throws UnsupportedEncodingException
	 * @throws IllegalStateException        如果客户端连接未建立
	 */
	public synchronized void sendMail(String recipientName, String recipientEmail, String subject, String content)
			throws MessagingException, UnsupportedEncodingException {

		// 封装邮件请求数据对象，便于扩展和统一处理
		MailRequest mailRequest = new MailRequest().toName(recipientName).toMailAddress(recipientEmail).subject(subject)
				.textContent(content);
		sendMail(mailRequest);
	}

	/**
	 * 发送邮件。
	 *
	 * @param mailRequest 邮件发送请求数据对象
	 * @throws MessagingException           构建邮件对象或发送邮件失败时抛出
	 * @throws UnsupportedEncodingException
	 * @throws IllegalStateException        如果客户端连接未建立
	 */
	public synchronized void sendMail(MailRequest mailRequest) throws MessagingException, UnsupportedEncodingException {
		String recipientName = mailRequest.toName;
		String recipientEmail = mailRequest.ToMailAddress;
		String subject = mailRequest.subject;
		String content = mailRequest.textContent;

		// 状态校验：当为真实连接模式时，确保底层 transport 已经 open 并处于已连接状态
		if (!SmtpDebugFlag.SIMULATION.getValue().equals(debugFlag)
				&& (this.transport == null || !this.transport.isConnected())) {
			throw new IllegalStateException(MessageUtils.getMessage("smtp.error.not.connected"));
		}

		// 参数校验：收件人邮箱地址不能为空
		if (recipientEmail == null || recipientEmail.isEmpty()) {
			throw new IllegalArgumentException(MessageUtils.getMessage("smtp.error.to.empty"));
		}

		// 拦截模式：仅在控制台输出待发送的邮件日志详情
		if (SmtpDebugFlag.SIMULATION.getValue().equals(debugFlag)) {
			logger.info(MessageUtils.getMessage("smtp.send.simulation.header"));
			logger.info(MessageUtils.getMessage("smtp.send.simulation.sender", senderName, senderEmail));
			logger.info(MessageUtils.getMessage("smtp.send.simulation.recipient", recipientName, recipientEmail));
			logger.info(MessageUtils.getMessage("smtp.send.simulation.subject", subject));
			logger.info(MessageUtils.getMessage("smtp.send.simulation.content", content));
		}

		// 异常模拟模式：抛出异常，模拟邮件发送失败
		if (SendDebugFlag.SEND_FAILURE.getValue().equals(sendDebugFlag)) {
			throw new MessagingException("【异常模拟模式】模拟SMTP发送失败。");
		}

		// 基于当前 Session 构建符合 MIME 规范的标准邮件消息体
		MimeMessage message = new MimeMessage(this.session);

		// 设置发件人信息，使用指定的个人别名并使用 UTF-8 进行国际化编码
		message.setFrom(new InternetAddress(senderEmail, senderName, "UTF-8"));

		// 设置主收件人 (TO) 属性，同样支持接收人昵称的国际化编码
		message.setRecipient(Message.RecipientType.TO, new InternetAddress(recipientEmail, recipientName, "UTF-8"));

		// 设置邮件标题主题，使用 UTF-8 编码防乱码
		message.setSubject(subject, "UTF-8");

		// 设置邮件正文内容为 text/html 格式，并且显式指明字符集为 UTF-8
		message.setContent(content, "text/html;charset=UTF-8");

		// 执行邮件的发送/模拟逻辑
		if (SmtpDebugFlag.SIMULATION.getValue().equals(debugFlag)) {
			logger.info(MessageUtils.getMessage("smtp.send.simulation.detail", recipientName, recipientEmail, subject));
		} else {
			// 在已建立的 transport 通道上传输邮件消息
			this.transport.sendMessage(message, message.getAllRecipients());
			logger.info(MessageUtils.getMessage("smtp.send.success", recipientName, recipientEmail));
		}
	}

	/**
	 * 关闭与 SMTP 服务器的连接，释放底层资源。 此方法实现了 {@link AutoCloseable#close()} 接口，以方便在
	 * try-with-resources 中使用。
	 */
	@Override
	public synchronized void close() {
		// 拦截模式：仅重置 Session 并打印模拟日志
		if (SmtpDebugFlag.SIMULATION.getValue().equals(debugFlag)) {
			logger.info(MessageUtils.getMessage("smtp.close.simulation"));
			this.session = null;
			return;
		}

		// 真实连接模式下，安全关闭 Transport 网络通道并重置资源状态
		if (transport != null) {
			try {
				if (transport.isConnected()) {
					logger.info(MessageUtils.getMessage("smtp.close.closing", host, port));
					this.transport.close();
				}
			} catch (MessagingException e) {
				logger.error(MessageUtils.getMessage("smtp.close.failed", host, e.getMessage()), e);
			} finally {
				// 最终将 transport 和 session 变量置为 null，便于 JVM 垃圾回收
				this.transport = null;
				this.session = null;
			}
		}
	}

	/**
	 * 获取 SMTP 服务器的主机名或 IP 地址。
	 *
	 * @return 服务器主机名或 IP 地址
	 */
	public String getHost() {
		return host;
	}

	/**
	 * 获取 SMTP 服务器端口。
	 *
	 * @return 服务端口号
	 */
	public int getPort() {
		return port;
	}

	/**
	 * 获取认证用户名。
	 *
	 * @return 用户名
	 */
	public String getUsername() {
		return username;
	}

	/**
	 * 获取发件人姓名。
	 *
	 * @return 发件人姓名
	 */
	public String getSenderName() {
		return senderName;
	}

	/**
	 * 获取发件人邮箱地址。
	 *
	 * @return 发件人邮箱地址
	 */
	public String getSenderEmail() {
		return senderEmail;
	}

	/**
	 * 获取调试拦截标志。
	 *
	 * @return 调试拦截标志（"0" 表示模拟发送，"1" 表示真实发送）
	 */
	public String getDebugFlag() {
		return debugFlag;
	}
}
