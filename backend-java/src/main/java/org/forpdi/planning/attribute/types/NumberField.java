package org.forpdi.planning.attribute.types;

import java.text.NumberFormat;

import javax.validation.UnexpectedTypeException;

import org.forpdi.core.properties.CoreMessages;
import org.forpdi.planning.attribute.AttributeType;
import org.forpdi.planning.attribute.AttributeTypeWrapper;

public class NumberField extends AttributeType {

	private AttributeTypeWrapper wrapper = new Wrapper();

	public final String WIDGET_NAME = "NumberField";
	@Override
	public String getWidget() {
		return WIDGET_NAME;
	}
	
	@Override
	public AttributeTypeWrapper getWrapper() {
		return wrapper;
	}

	@Override
	public String getDisplayName() {
		return "Número";
	}

	public static class Wrapper implements AttributeTypeWrapper {
		
		NumberFormat formatter = NumberFormat.getNumberInstance(CoreMessages.DEFAULT_LOCALE);
		
		@Override
		public String fromDatabase(String databaseValue) {
			return databaseValue;
		}

		@Override
		public String fromDatabaseNumerical(Double databaseValue) {
			return formatter.format(databaseValue);
		}

		@Override
		public String toDatabase(String viewValue) {
			return viewValue.trim();
		}
		@Override
		public Double toDatabaseNumerical(String viewValue) {
			try {
				return formatter.parse(viewValue).doubleValue();
			} catch(Exception ex) {
				throw new UnexpectedTypeException(ex);
			}
		}

		@Override
		public boolean isNumerical() {
			return true;
		}
		@Override
		public String prefix() {
			return "";
		}
		@Override
		public String suffix() {
			return "";
		}
	}
}
