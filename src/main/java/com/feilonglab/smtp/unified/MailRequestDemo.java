package com.feilonglab.smtp.unified;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.feilonglab.smtp.basic.SmtpClient;

/**
 * MailRequest 邮件发送请求的使用演示类。
 * <p>
 * 本类展示了如何使用链式调用（Fluent API）方式构建 {@link MailRequest}，
 * 并结合 {@link SmtpClient} 完成邮件发送。
 * </p>
 *
 * @author feilonglab
 * @version 0.1.1
 */
public class MailRequestDemo {

	/** 日志记录器 */
	private static final Logger logger = LoggerFactory.getLogger(MailRequestDemo.class);

	/**
	 * 主函数，演示如何使用 MailRequest 构建邮件发送请求并通过 SmtpClient 发送。
	 * 
	 * @param args
	 */
	public static void main(String[] args) {
		logger.info("开始执行【MailRequest 发送示例】...");

		// 1. 使用链式调用构建邮件发送请求数据对象 (MailRequest)
		MailRequest mailRequest = new MailRequest()
				.toName("测试收件人")
				.toMailAddress("recipient@example.com")
				.subject("测试统一邮件请求")
				.textContent("<h1>这是一封使用 MailRequest 构建的邮件</h1><p>通过 SmtpClient 成功发送！</p>");

		// 2. 使用 try-with-resources 自动管理 SmtpClient 生命周期与连接释放
		try (SmtpClient client = new SmtpClient()) {

			// 步骤一：连接 SMTP 服务器。捕获并处理连接阶段的异常
			try {
				client.open();
			} catch (Exception e) {
				logger.error("【连接错误】无法建立与 SMTP 服务器的连接！", e);
				return; // 连接失败时中断执行
			}

			// 步骤二：发送邮件请求。捕获并处理发送阶段的异常
			try {
				client.sendMail(mailRequest);
				logger.info("【发送成功】邮件已成功通过 MailRequest 发送！");
			} catch (Exception e) {
				logger.error("【发送错误】邮件发送失败！", e);
			}

		} catch (Exception e) {
			logger.error("【资源关闭错误】关闭 SmtpClient 时发生异常！", e);
		}
	}
}
