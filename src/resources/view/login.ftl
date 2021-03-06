<#ftl output_format='HTML'>
<!DOCTYPE html>
<html>
<head>
<title>${getText('login')}</title>
<meta name="body_class" content="welcome" />
<#assign anonymous = false>
<@authorize ifAllGranted="ROLE_BUILTIN_ANONYMOUS">
<#assign anonymous = true>
</@authorize>
<#if !anonymous>
<meta name="decorator" content="simple" />
<meta http-equiv="refresh" content="0; url=<@url value=targetUrl!(properties['login.defaultTargetUrl']!'/')/>" />
</#if>
</head>
<body>
<#if anonymous>
<div class="row">
	<div class="span6 offset3">
	<h2 class="caption">${getText('login')}</h2>
	<div class="hero-unit">
	<#assign verificationCodeEnabled = (properties['verification.code.enabled']!)=='true'>
	<#assign verificationCodeRequired = false>
	<#assign passwordRequired = true>
	<#if verificationCodeEnabled && username?has_content>
		<#assign verificationCodeRequired = verificationManager.isVerificationRequired(username)>
		<#assign passwordRequired = verificationManager.isPasswordRequired(username)>
	</#if>
	<#assign totpEnabled = (properties['totp.enabled']!)=='true'>
	<@s.form id="login" action=request.requestURI+request.queryString???then('?'+request.queryString,'') method="post" class="ajax focus form-horizontal well">
		<#assign dynamicAttributes={}>
		<#if verificationCodeEnabled>
		<#assign dynamicAttributes+={'data-replacement':'verification'}>
		</#if>
		<@s.textfield name="username" class="required span2${verificationCodeEnabled?then(' conjunct','')}" dynamicAttributes=dynamicAttributes/>
		<#if verificationCodeEnabled><div id="verification"></#if>
		<#if passwordRequired>
		<@s.password name="password" class="required span2 input-pattern submit sha"/>
		</#if>
		<#if verificationCodeRequired || totpEnabled>
		<@s.textfield name="verificationCode" class="required input-small" maxlength="${properties['verification.code.length']!'6'}">
			<#if verificationCodeRequired>
			<@s.param name="after"> <button type="button" class="btn input-mini sendVerificationCode" data-interval="${properties['verification.code.resend.interval']!'60'}">${getText('send')}</button></@s.param>
			</#if>
		</@s.textfield>
		<#else>
		<#if (properties['rememberMe.disabled']!)!='true'><@s.checkbox name="rememberme" class="switch span2"/></#if>
		<@captcha/>
		</#if>
		<#if verificationCodeEnabled></div></#if>
		<@s.submit label=getText('login') class="btn-primary"/>
	</@s.form>
	</div>
	</div>
</div>
<#else>
<div class="modal">
	<div class="modal-body">
		<div class="progress progress-striped active">
			<div class="bar" style="width: 50%;"></div>
		</div>
	</div>
</div>
</#if>
</body>
</html>
