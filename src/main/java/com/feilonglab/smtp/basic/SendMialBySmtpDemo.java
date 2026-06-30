package com.feilonglab.smtp.basic;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * SmtpClient 邮件发送使用演示类。
 * <p>
 * 本类展示了如何正确使用 {@link SmtpClient} 执行单封邮件发送以及多封邮件批量发送。
 * 重点演示了：
 * 1. 资源管理：结合 try-with-resources 自动关闭连接释放底层 TCP/Socket 资源。
 * 2. 异常隔离：清晰区分连接阶段错误和邮件发送阶段错误。
 * 3. 容错性控制：在批量发送中，某一封邮件的失败不会影响后续邮件的发送。
 * 4. 统一日志：统一采用 SLF4J/Log4j 框架记录日志。
 * </p>
 *
 * @author feilonglab
 * @version 0.1.0
 */
public class SendMialBySmtpDemo {

	/** 日志记录器 */
	private static final Logger logger = LoggerFactory.getLogger(SendMialBySmtpDemo.class);

	/**
	 * 主函数，演示单封邮件发送和多封邮件批量发送的使用方法。
	 * 
	 * @param args
	 */
	public static void main(String[] args) {
		// 执行单封邮件发送示例
		sendSingleMail();

		logger.info("========================================");

		// 执行多封邮件批量发送示例
		sendMultipleMails();
	}

	/**
	 * 演示单封邮件的发送过程。
	 * 采用嵌套 try-catch 以精细化区分服务器连接失败与邮件本体构建/发送失败。
	 */
	private static void sendSingleMail() {
		logger.info("开始执行【单个邮件发送示例】...");
		// try-with-resources 语句确保 client.close() 在执行完毕后无论成功与否都会被调用
		try (SmtpClient client = new SmtpClient()) {

			// 步骤一：连接服务器。此处捕获连接阶段的异常
			try {
				client.open();
			} catch (Exception e) {
				logger.error("【连接错误】无法建立与 SMTP 服务器的连接！", e);
				return; // 连接失败，后续的发送步骤无法进行，直接中断返回
			}

			// 准备邮件数据
			String recipientName = "测试收件人";
			String recipientEmail = "recipient@example.com";
			String subject = "测试单个邮件主题";
			String content = "<h1>这是一封测试邮件</h1><p>通过 SmtpClient 成功发送！</p>";

			// 步骤二：发送邮件。此处捕获发送阶段的异常
			try {
				client.sendMail(recipientName, recipientEmail, subject, content);
			} catch (Exception e) {
				logger.error("【发送错误】单个邮件发送失败！", e);
			}

		} catch (Exception e) {
			logger.error("【资源关闭错误】关闭 SmtpClient 资源时发生异常！", e);
		}
	}

	/**
	 * 演示多封邮件的批量发送过程。
	 * 重用同一个客户端实例以复用底层的 TCP 网络通道，从而避免频繁握手以提升性能。
	 * 并在循环体内部捕获异常，确保单封邮件的发送失败不会影响整体批处理的执行。
	 */
	private static void sendMultipleMails() {
		logger.info("开始执行【复数邮件发送示例】...");
		// 构造待发送的收件人列表数据
		List<Recipient> recipients = List.of(
				new Recipient("张三", "zhangsan@example.com"),
				new Recipient("李四", "lisi@example.com"),
				new Recipient("王五", "wangwu@example.com"));

		// 声明同一个 SmtpClient 以复用连接
		try (SmtpClient client = new SmtpClient()) {

			// 步骤一：连接服务器。如果连接失败，则中断批量发送逻辑
			try {
				client.open();
			} catch (Exception e) {
				logger.error("【连接错误】批量发送初始化失败：无法建立与 SMTP 服务器的连接！", e);
				return; // 初始连接失败，不执行后续循环发送
			}

			// 步骤二：循环批量发送。在循环体内捕捉异常，保证发送链的连续性与鲁棒性
			for (int i = 0; i < recipients.size(); i++) {
				Recipient r = recipients.get(i);
				String subject = "批量邮件测试 - 第 " + (i + 1) + " 封";
				String content = String.format("<h1>你好 %s</h1><p>这是发送给您的第 %d 封批量测试邮件。</p>", r.name, i + 1);

				try {
					client.sendMail(r.name, r.email, subject, content);
				} catch (Exception e) {
					// 仅记录并输出当前个体的发送错误，不 throw 抛出，确保循环继续执行
					logger.error("【发送错误】发送给 {} <{}> 的邮件（第 {} 封）失败！", r.name, r.email, i + 1, e);
				}
			}

		} catch (Exception e) {
			logger.error("【资源关闭错误】关闭 SmtpClient 资源时发生异常！", e);
		}
	}

	/**
	 * 收件人辅助封装类。
	 */
	private static class Recipient {
		final String name;
		final String email;

		Recipient(String name, String email) {
			this.name = name;
			this.email = email;
		}
	}

}
