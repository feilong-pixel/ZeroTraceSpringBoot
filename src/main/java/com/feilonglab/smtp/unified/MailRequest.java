package com.feilonglab.smtp.unified;

/**
 * 统一邮件发送请求数据传输对象 (DTO)。
 * <p>
 * 该类采用链式调用设计 (Builder-like Pattern)，以便于灵活、优雅地构建复杂的邮件发送请求。
 * 支持以下组合配置：
 * 1. 多个 TO、CC、BCC 收件人。
 * 2. 文本正文和 HTML 正文。
 * 3. 动态 HTML 模板文件路径及参数映射。
 * 4. 多附件关联。
 * </p>
 *
 * @author feilonglab
 * @version 0.1.1
 */
public class MailRequest {

	/** 主要收件人名 (ToName) */
	public String toName;

	/** 主要收件人邮件地址 (ToMailAddress) */
	public String ToMailAddress;

	/** 邮件主题 */
	public String subject;

	/** 邮件纯文本备用正文内容 */
	public String textContent;

	// ==================== 链式调用 Setter 方法 ====================

	/**
	 * 设置主要收件人名。
	 *
	 * @param toName 收件人姓名
	 * @return 当前请求对象，支持链式调用
	 */
	public MailRequest toName(String toName) {
		this.toName = toName;
		return this;
	}

	/**
	 * 设置主要收件人邮箱地址。
	 *
	 * @param toMailAddress 收件人邮箱地址
	 * @return 当前请求对象，支持链式调用
	 */
	public MailRequest toMailAddress(String toMailAddress) {
		this.ToMailAddress = toMailAddress;
		return this;
	}

	/**
	 * 设置邮件主题。
	 *
	 * @param subject 邮件主题
	 * @return 当前请求对象，支持链式调用
	 */
	public MailRequest subject(String subject) {
		this.subject = subject;
		return this;
	}

	/**
	 * 设置纯文本格式的正文内容（可作为备用内容）。
	 *
	 * @param textContent 纯文本正文内容
	 * @return 当前请求对象，支持链式调用
	 */
	public MailRequest textContent(String textContent) {
		this.textContent = textContent;
		return this;
	}
}
