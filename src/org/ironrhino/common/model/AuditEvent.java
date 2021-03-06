package org.ironrhino.common.model;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;

import org.apache.commons.lang3.StringUtils;
import org.hibernate.annotations.CreationTimestamp;
import org.ironrhino.core.event.AbstractAuditEvent;
import org.ironrhino.core.metadata.Authorize;
import org.ironrhino.core.metadata.AutoConfig;
import org.ironrhino.core.metadata.Readonly;
import org.ironrhino.core.metadata.Richtable;
import org.ironrhino.core.metadata.UiConfig;
import org.ironrhino.core.model.BaseEntity;
import org.ironrhino.core.search.elasticsearch.annotations.Index;
import org.ironrhino.core.search.elasticsearch.annotations.Searchable;
import org.ironrhino.core.search.elasticsearch.annotations.SearchableProperty;
import org.ironrhino.core.security.role.UserRole;
import org.ironrhino.core.struts.I18N;
import org.ironrhino.core.util.JsonUtils;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@AutoConfig(namespace = "/")
@Authorize(ifAnyGranted = { UserRole.ROLE_ADMINISTRATOR, UserRole.ROLE_AUDITOR })
@Entity
@Table(indexes = { @javax.persistence.Index(columnList = "username,date desc") })
@Searchable
@Richtable(searchable = true, order = "date desc", readonly = @Readonly(true))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AuditEvent extends BaseEntity {

	private static final long serialVersionUID = -7691080078972338500L;

	@UiConfig(width = "100px")
	@Column(nullable = false)
	@SearchableProperty(index = Index.NOT_ANALYZED)
	private String username;

	@UiConfig(width = "130px")
	@CreationTimestamp
	@Column(name = "date")
	private Date date;

	@UiConfig(width = "150px")
	@SearchableProperty
	private String address;

	@UiConfig(template = "${entity.displayEvent!}")
	@SearchableProperty
	@Column(name = "event", nullable = false)
	private String event;

	public AuditEvent(String username, Date date, String address, String eventKey, String... arguments) {
		this.username = username;
		this.address = address;
		if (date != null)
			this.date = date;
		if (arguments == null || arguments.length == 0) {
			this.event = eventKey;
		} else {
			Map<String, String> map = new HashMap<>();
			map.put("key", eventKey);
			map.put("arguments", String.join(",", arguments));
			this.event = JsonUtils.toJson(map);
		}
	}

	public AuditEvent(AbstractAuditEvent abstractEvent) {
		this(abstractEvent.getUsername(), new Date(abstractEvent.getTimestamp()), abstractEvent.getRemoteAddr(),
				abstractEvent.getClass().getName(), abstractEvent.getArguments());
	}

	public String getDisplayEvent() {
		if (StringUtils.isBlank(event))
			return "";
		if (JsonUtils.isValidJson(event)) {
			try {
				Map<String, String> map = JsonUtils.fromJson(event, JsonUtils.STRING_MAP_TYPE);
				String key = map.get("key");
				String[] arguments = map.get("arguments").split(",");
				return I18N.getText(key, arguments);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return I18N.getText(event);
	}
}
