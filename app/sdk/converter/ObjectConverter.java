package sdk.converter;

import org.joda.time.DateTime;
import sdk.annotations.*;
import sdk.converter.attachment.ApptreeAttachment;
import sdk.data.*;
import sdk.exceptions.DestinationInvalidException;
import sdk.exceptions.UnableToWriteException;
import sdk.exceptions.UnsupportedAttributeException;
import sdk.list.ListItem;
import sdk.models.AttributeType;
import sdk.models.Color;
import sdk.models.Location;
import sdk.utils.RecordUtils;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Type;
import java.util.*;

import static sdk.utils.ClassUtils.Null;

/**
 * Created by Orozco on 7/19/17.
 */
public class ObjectConverter extends ConfigurationManager {
    private static ParserContext parserContext;


    public ObjectConverter() {
    }


    public static <T> DataSet getDataSetFromObject(T t, Collection<ServiceConfigurationAttribute> attributes) {
        DataSetItem dataSetItem = new DataSetItem(attributes);
        copyToRecord(dataSetItem, t);
        return new DataSet(dataSetItem);
    }


    /**
     * @param objects
     * @param attributes
     * @param <T>
     * @return
     */
    public static <T> DataSet getDataSetFromCollection(Collection<T> objects, Collection<ServiceConfigurationAttribute> attributes) {
        DataSet dataSet = new DataSet(attributes);
        for (T object : objects) {
            DataSetItem dataSetItem = dataSet.addNewDataSetItem();
            copyToRecord(dataSetItem, object);
        }
        return dataSet;
    }


    /**
     * Method accepts an object that implements the `Record` interface and an object's members annotated with Attribute annotation
     * This function iterates the annotated members of the "destination" object
     * then copies the value from record object into the annotated field of your "destination" object
     * The link is created by index
     *
     * @param record
     * @param destination
     * @param <T>
     */
    public static <T> ParserContext copyFromRecord(Record record, T destination) {
        ParserContext parserContext = getParserContext();
        mapMethodsFromSource(destination);
        if (destination == null) {
            throw new DestinationInvalidException();
        }
        if (record.supportsCRUDStatus()) {
            parserContext.setItemStatus(destination, record.getCRUDStatus());
        }
        for (AttributeProxy proxy : getMethodAndFieldAnnotationsForClass(destination.getClass())) {
            try {
                copyToField(proxy, record, destination, parserContext);
                if (proxy.isPrimaryKey()) proxy.setPrimaryKeyOrValue(destination, record.getPrimaryKey());
                if (proxy.isPrimaryValue()) proxy.setPrimaryKeyOrValue(destination, record.getValue());
            } catch (UnsupportedAttributeException | IllegalAccessException | UnableToWriteException | InvocationTargetException e) {
                e.printStackTrace();
            }
        }
        return parserContext;
    }


    private static ParserContext getParserContext() {
        if (parserContext == null) {
            parserContext = new ParserContext();
        }
        return parserContext;
    }


    /**
     * @param dataSetItem
     * @param source
     * @param <T>
     */
    public static <T> void copyToRecord(Record dataSetItem, T source) {
        if (source == null) return;
        mapMethodsFromSource(source);
        for (AttributeProxy attributeProxy : getMethodAndFieldAnnotationsForClass(source.getClass())) {
            try {
                copyFromField(attributeProxy, dataSetItem, source);
            } catch (UnsupportedAttributeException | IllegalAccessException | InvocationTargetException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * @param proxy
     * @param record
     * @param destination
     * @param <T>
     * @throws UnsupportedAttributeException
     * @throws IllegalAccessException
     * @throws UnableToWriteException
     * @throws InvocationTargetException
     */
    private static <T> void copyToField(AttributeProxy proxy, Record record, T destination, ParserContext parserContext) throws UnsupportedAttributeException, IllegalAccessException, UnableToWriteException, InvocationTargetException {
        if (!proxy.isAttribute() && !proxy.isRelationship()) {
            return;
        }
        if (record.isListItem() && proxy.excludeFromList()) return;

        int index = proxy.getIndex();
        Class fieldClass = proxy.getType();
        AttributeMeta attributeMeta = record.getAttributeMeta(index);
        boolean userSetterAndGetter = proxy.useSetterAndGetter();
        if (attributeMeta == null) {
            attributeMeta = inferMetaData(index, fieldClass);
        }
        if (!isFieldClassSupportedForType(fieldClass, attributeMeta.getAttributeType())) {
            throw new UnsupportedAttributeException(fieldClass, attributeMeta.getAttributeType());
        }
        readDataSetItemData(proxy, attributeMeta, destination, record, userSetterAndGetter, parserContext);
    }

    /**
     * @param attributeProxy
     * @param record
     * @param source
     * @param <T>
     * @throws UnsupportedAttributeException
     * @throws IllegalAccessException
     * @throws InvocationTargetException
     */
    private static <T> void copyFromField(AttributeProxy attributeProxy, Record record, T source) throws UnsupportedAttributeException, IllegalAccessException, InvocationTargetException {
        boolean primaryKey = false;
        boolean value = false;
        boolean parentValue = false;

        boolean excludeFromListItem = attributeProxy.excludeFromList();
        if (record.isListItem() && attributeProxy.excludeFromList()) return;

        primaryKey = attributeProxy.isPrimaryKey();
        value = attributeProxy.isPrimaryValue();
        parentValue = attributeProxy.isParentValue();
        if (attributeProxy.isPrimaryKey()) {
            primaryKey = true;
        }
        if (attributeProxy.isPrimaryValue()) {
            value = true;
        }

        if (primaryKey && !attributeProxy.isAttribute()) {
            record.setPrimaryKey(attributeProxy.getValue(source).toString());
        }

        int index = attributeProxy.getIndex();
        Class fieldClass = attributeProxy.getType();
        boolean useGetterAndSetter = attributeProxy.useSetterAndGetter();
        AttributeMeta attributeMeta = record.getAttributeMeta(index);
        if (attributeMeta == null) {
            attributeMeta = new AttributeMeta(inferDataType(attributeProxy.getType().getSimpleName()).getAttributeType(), index);
        }
        if (!isFieldClassSupportedForType(fieldClass, attributeMeta.getAttributeType())) {
            throw new UnsupportedAttributeException(fieldClass, attributeMeta.getAttributeType());
        }
        readObjectData(attributeProxy, attributeMeta, source, record, primaryKey, useGetterAndSetter, value, parentValue);
    }

    /**
     * @param proxy
     * @param attributeMeta
     * @param destination
     * @param dataSetItem
     * @param useSetterAndGetter
     * @param <T>
     * @throws UnableToWriteException
     * @throws InvocationTargetException
     */
    private static <T> void readDataSetItemData(AttributeProxy proxy, AttributeMeta attributeMeta, T destination, Record dataSetItem, boolean useSetterAndGetter, ParserContext parserContext) throws UnableToWriteException, InvocationTargetException {
        switch (attributeMeta.getAttributeType()) {
            case String:
                writeStringData(proxy, destination, dataSetItem, attributeMeta.getAttributeIndex(), useSetterAndGetter);
                break;
            case Int:
                writeIntegerData(proxy, destination, dataSetItem, attributeMeta.getAttributeIndex());
                break;
            case Double:
                writeDoubleData(proxy, destination, dataSetItem, attributeMeta.getAttributeIndex());
                break;
            case Boolean:
                writeBoolData(proxy, destination, dataSetItem, attributeMeta.getAttributeIndex());
                break;
            case Date:
                writeDateData(proxy, destination, dataSetItem, attributeMeta.getAttributeIndex());
                break;
            case DateTime:
                writeDateTimeData(proxy, destination, dataSetItem, attributeMeta.getAttributeIndex());
                break;
            case ListItem:
                writeListItemData(proxy, destination, dataSetItem, attributeMeta.getAttributeIndex());
                break;
            case SingleRelationship:
                writeSingleRelationshipData(proxy, destination, dataSetItem, attributeMeta.getAttributeIndex(), parserContext);
                break;
            case Relation:
                writeRelationshipData(proxy, destination, dataSetItem, attributeMeta.getAttributeIndex(), parserContext);
                break;
            case Attachments:
                writeAttachmentData(proxy, destination, dataSetItem, attributeMeta.getAttributeIndex(), parserContext);
                break;
            case Location:
                routeWriteLocationData(proxy, destination, dataSetItem, attributeMeta.getAttributeIndex());
                break;
            case Color:
                writeColorData(proxy, destination, dataSetItem, attributeMeta.getAttributeIndex());
                break;
            default:
                writeStringData(proxy, destination, dataSetItem, attributeMeta.getAttributeIndex(), useSetterAndGetter);
                break;
        }
    }

    /**
     * @param attributeProxy
     * @param attributeMeta
     * @param object
     * @param dataSetItem
     * @param primaryKey
     * @param useGetterAndSetter
     * @param value
     * @param <T>
     * @throws IllegalAccessException
     * @throws InvocationTargetException
     */
    private static <T> void readObjectData(AttributeProxy attributeProxy, AttributeMeta attributeMeta, T object, Record dataSetItem, boolean primaryKey, boolean useGetterAndSetter, boolean value, boolean parentValue) throws IllegalAccessException, InvocationTargetException {
        switch (attributeMeta.getAttributeType()) {
            case String:
                readStringData(attributeProxy, object, dataSetItem, attributeMeta.getAttributeIndex(), primaryKey, useGetterAndSetter, value, parentValue);
                break;
            case Int:
                readIntegerData(attributeProxy, object, dataSetItem, attributeMeta.getAttributeIndex(), primaryKey, useGetterAndSetter, value, parentValue);
                break;
            case Double:
                readDoubleData(attributeProxy, object, dataSetItem, attributeMeta.getAttributeIndex(), primaryKey, useGetterAndSetter, value);
                break;
            case Boolean:
                readBoolData(attributeProxy, object, dataSetItem, attributeMeta.getAttributeIndex(), primaryKey, useGetterAndSetter, value);
                break;
            case Date:
                readDateData(attributeProxy, object, dataSetItem, attributeMeta.getAttributeIndex(), primaryKey, useGetterAndSetter, value);
                break;
            case DateTime:
                readDateTimeData(attributeProxy, object, dataSetItem, attributeMeta.getAttributeIndex(), primaryKey, useGetterAndSetter, value);
                break;
            case ListItem:
                readListItemData(attributeProxy, object, dataSetItem, attributeMeta.getAttributeIndex(), useGetterAndSetter);
                break;
            case Relation:
                readRelationshipData(attributeProxy, object, dataSetItem, attributeMeta.getAttributeIndex(), useGetterAndSetter);
                break;
            case SingleRelationship:
                readSingleRelationshipData(attributeProxy, object, dataSetItem, attributeMeta.getAttributeIndex(), useGetterAndSetter);
                break;
            case Attachments:
                readAttachmentData(attributeProxy, object, dataSetItem, attributeMeta.getAttributeIndex(), useGetterAndSetter);
                break;
            case Location:
                readLocationData(attributeProxy, object, dataSetItem, attributeMeta.getAttributeIndex(), primaryKey, useGetterAndSetter, value);
                break;
            case Color:
                readColorData(attributeProxy, object, dataSetItem, attributeMeta.getAttributeIndex(), useGetterAndSetter);
                break;
            default:
                break;
        }
    }

    /**
     * @param proxy
     * @param destination
     * @param dataSetItem
     * @param index
     * @param useSetterAndGetter
     * @param <T>
     * @throws UnableToWriteException
     * @throws InvocationTargetException
     */
    private static <T> void writeStringData(AttributeProxy proxy, T destination, Record dataSetItem, Integer index, boolean useSetterAndGetter) throws UnableToWriteException, InvocationTargetException {
        String value = dataSetItem.getString(index);
        try {
            useSetterIfExists(proxy, destination, value);
        } catch (IllegalAccessException e) {
            throw new UnableToWriteException(proxy.getType().getClass().getName(), index, AttributeType.String.toString(), e.getMessage());
        }
    }

    private static <T> void writeIntegerData(AttributeProxy proxy, T destination, Record dataSetItem, Integer index) throws UnableToWriteException {
        Optional<Integer> value = dataSetItem.getOptionalInt(index);
        Integer intValue = 0;
        try {
            if (value.isPresent()) {
                intValue = value.get();
            } else {
                ConverterAttributeType converterAttributeType = inferDataType(proxy.getType().getSimpleName());
                intValue = (converterAttributeType.isOptional()) ? null : 0;
            }
            useSetterIfExists(proxy, destination, intValue);
        } catch (IllegalAccessException e) {
            throw new UnableToWriteException(proxy.getType().getName(), index, AttributeType.Int.toString(), e.getMessage());
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }
    }

    /**
     * @param proxy
     * @param destination
     * @param dataSetItem
     * @param index
     * @param <T>
     * @throws UnableToWriteException
     * @throws InvocationTargetException
     */
    private static <T> void writeDoubleData(AttributeProxy proxy, T destination, Record dataSetItem, Integer index) throws UnableToWriteException, InvocationTargetException {
        Optional<Double> value = dataSetItem.getOptionalDouble(index);
        boolean isFloatValue = fieldIsFloat(proxy.getType());
        try {
            if (value.isPresent()) {
                if (isFloatValue) {
                    useSetterIfExists(proxy, destination, value.get().floatValue());
                } else {
                    useSetterIfExists(proxy, destination, value.get());
                }

            } else {
                ConverterAttributeType converterAttributeType = inferDataType(proxy.getType().getSimpleName());
                if (converterAttributeType.isOptional()) {
                    proxy.setValue(destination, null);
                } else {
                    if (isFloatValue)
                        useSetterIfExists(proxy, destination, 0.0f);
                    else useSetterIfExists(proxy, destination, 0.0);
                }
            }
        } catch (IllegalAccessException e) {
            throw new UnableToWriteException(proxy.getType().getName(), index, AttributeType.Double.toString(), e.getMessage());
        }
    }

    /**
     * @param proxy
     * @param destination
     * @param dataSetItem
     * @param index
     * @param <T>
     * @throws UnableToWriteException
     * @throws InvocationTargetException
     */
    private static <T> void writeBoolData(AttributeProxy proxy, T destination, Record dataSetItem, Integer index) throws UnableToWriteException, InvocationTargetException {
        Optional<Boolean> value = dataSetItem.getOptionalBoolean(index);
        try {
            if (value.isPresent()) {
                useSetterIfExists(proxy, destination, value.get());
            } else {
                ConverterAttributeType converterAttributeType = inferDataType(proxy.getType().getSimpleName());
                useSetterIfExists(proxy, destination, (converterAttributeType.isOptional()) ? null : false);
            }
        } catch (IllegalAccessException e) {
            throw new UnableToWriteException(proxy.getType().getName(), index, AttributeType.Boolean.toString(), e.getMessage());
        }
    }

    /**
     * @param proxy
     * @param destination
     * @param dataSetItem
     * @param index
     * @param <T>
     * @throws UnableToWriteException
     * @throws InvocationTargetException
     */
    private static <T> void writeDateData(AttributeProxy proxy, T destination, Record dataSetItem, Integer index) throws UnableToWriteException, InvocationTargetException {
        DateTime value = dataSetItem.getDate(index);
        try {
            setDateValueFromField(proxy, value, destination);
        } catch (IllegalAccessException e) {
            throw new UnableToWriteException(proxy.getType().getName(), index, AttributeType.Date.toString(), e.getMessage());
        }
    }

    /**
     * @param proxy
     * @param destination
     * @param dataSetItem
     * @param index
     * @param <T>
     * @throws UnableToWriteException
     * @throws InvocationTargetException
     */
    private static <T> void writeDateTimeData(AttributeProxy proxy, T destination, Record dataSetItem, Integer index) throws UnableToWriteException, InvocationTargetException {
        DateTime value = dataSetItem.getDateTime(index);
        try {
            setDateValueFromField(proxy, value, destination);
        } catch (IllegalAccessException e) {
            throw new UnableToWriteException(proxy.getType().getName(), index, AttributeType.DateTime.toString(), e.getMessage());
        }
    }

    /**
     * @param proxy
     * @param destination
     * @param dataSetItem
     * @param index
     * @param <T>
     * @throws UnableToWriteException
     * @throws InvocationTargetException
     */
    private static <T> void writeListItemData(AttributeProxy proxy, T destination, Record dataSetItem, Integer index) throws UnableToWriteException, InvocationTargetException {
        ListItem listItem = dataSetItem.getListItem(index);
        if (listItem == null) return;
        Type fieldType = proxy.getType();
        Class classValue = null;
        copyFromRecordRecursive(proxy, fieldType, classValue, listItem, destination, index);
    }

    /**
     * @param proxy
     * @param destination
     * @param record
     * @param index
     * @param <T>
     * @throws UnableToWriteException
     * @throws InvocationTargetException
     */
    private static <T> void writeSingleRelationshipData(AttributeProxy proxy, T destination, Record record, Integer index, ParserContext parserContext) throws UnableToWriteException, InvocationTargetException {
        parserContext.setItemStatus(destination, record.getCRUDStatus());
        DataSetItem newDataSetItem = record.getDataSetItem(index);
        if (newDataSetItem == null) return;
        Type fieldType = proxy.getType();
        Class classValue = null;
        copyFromRecordRecursive(proxy, fieldType, classValue, newDataSetItem, destination, index);
    }

    private static <T> void copyFromRecordRecursive(AttributeProxy proxy, Type fieldType, Class classValue, Record record, T destination, Integer index)
            throws InvocationTargetException, UnableToWriteException {
        try {
            classValue = Class.forName(fieldType.getTypeName());
            Object object = classValue.newInstance();
            copyFromRecord(record, object);
            useSetterIfExists(proxy, destination, object);
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException ie) {
            throw new UnableToWriteException(classValue.getName(), index, AttributeType.ListItem.toString(), ie.getMessage());
        }
    }

    /**
     * @param proxy
     * @param destination
     * @param record
     * @param index
     * @param <T>
     * @throws UnableToWriteException
     * @throws InvocationTargetException
     */
    private static <T> void writeRelationshipData(AttributeProxy proxy, T destination, Record record, Integer index, ParserContext parserContext) throws UnableToWriteException, InvocationTargetException {
        List<DataSetItem> dataSetItems = record.getDataSetItems(index);
        parserContext.setItemStatus(destination, record.getCRUDStatus());
        if (dataSetItems == null) return;
        Class classValue = null;
        try {
            classValue = Class.forName(proxy.getType().getName());
            ArrayList<Object> tempList = new ArrayList<>();
            for (DataSetItem dataSetItem1 : dataSetItems) {
                Object object = classValue.newInstance();
                copyFromRecord(dataSetItem1, object);
                tempList.add(object);
            }
            useSetterIfExists(proxy, destination, tempList);
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException ie) {
            throw new UnableToWriteException(classValue.getName(), index, AttributeType.ListItem.toString(), ie.getMessage());
        }
    }

    private static <T> void routeWriteLocationData(AttributeProxy proxy, T destination, Record dataSetItem, Integer index)
            throws UnableToWriteException, InvocationTargetException {
        if (CustomLocation.class.isAssignableFrom(proxy.getType()))
            writeCustomLocationData(proxy, destination, dataSetItem, index);
        else writeLocationData(proxy, destination, dataSetItem, index);
    }

    private static <T, C extends CustomLocation> void writeCustomLocationData(AttributeProxy proxy, T destination, Record dataSetItem, Integer index)
            throws UnableToWriteException, InvocationTargetException {
        Location location = dataSetItem.getLocation(index);
        if (location == null) return;
        try {
            Field field = destination.getClass().getDeclaredField(proxy.getName());
            Class<C> clazz = (Class<C>) Class.forName(field.getType().getName());
            C newInstance = clazz.newInstance();
            newInstance.fromLocation(location, newInstance);
            useSetterIfExists(proxy, destination, newInstance);
        } catch (Exception error) {
            System.out.println(error.getMessage());
        }
    }

    private static <T> void writeLocationData(AttributeProxy proxy, T destination, Record dataSetItem, Integer index)
            throws UnableToWriteException, InvocationTargetException {
        Location location = dataSetItem.getLocation(index);
        if (location == null) return;
        try {
            useSetterIfExists(proxy, destination, location);
        } catch (Exception error) {
            throw new RuntimeException("Unable to write Location data for field: " + proxy.getName());
        }
    }

    private static <T> void writeColorData(AttributeProxy proxy, T destination, Record record, Integer index) {
        Color color = record.getColor(index);
        if (color == null) return;
        try {
            useSetterIfExists(proxy, destination, color);
        } catch (Exception error) {
            throw new RuntimeException("Unable to write Color data for field: " + proxy.getName());
        }
    }


    private static <T> void writeAttachmentData(AttributeProxy proxy, T destination, Record record, Integer index, ParserContext parserContext) throws UnableToWriteException, InvocationTargetException {
        List<DataSetItemAttachment> attachmentItems = record.getAttachmentItemsForIndex(index);
        parserContext.setItemStatus(destination, record.getCRUDStatus());
        if (attachmentItems == null) return;
        try {
            ApptreeAttachment singleAttachment = (ApptreeAttachment) proxy.getType().newInstance();
            ArrayList<ApptreeAttachment> attachmentList = new ArrayList<>();
            if (proxy.isWrappedClass) {
                RecordUtils.copyListOfAttachmentsFromRecordForIndex(attachmentItems, attachmentList);
                useSetterIfExists(proxy, destination, attachmentList);
            } else {
                RecordUtils.copyAttachmentFromRecordForIndex(attachmentItems, singleAttachment);
                useSetterIfExists(proxy, destination, singleAttachment);
            }
        } catch (IllegalAccessException ie) {
            ie.printStackTrace();
            throw new UnableToWriteException(proxy.getType().getName(), index, AttributeType.ListItem.toString(), ie.getMessage());
        } catch (InstantiationException e) {
            e.printStackTrace();
        }
    }

    /**
     * @param proxy
     * @param datetime
     * @param destination
     * @param <T>
     * @throws IllegalAccessException
     * @throws InvocationTargetException
     */
    private static <T> void setDateValueFromField(AttributeProxy proxy, DateTime datetime, T destination) throws IllegalAccessException, InvocationTargetException {
        if (Null(datetime)) return;
        Class clazz = proxy.getType();
        if (clazz == org.joda.time.DateTime.class) {
            useSetterIfExists(proxy, destination, datetime);
        }
        if (clazz == java.util.Date.class) {
            useSetterIfExists(proxy, destination, new java.util.Date(datetime.getMillis()));
        }
        if (clazz == java.sql.Date.class) {
            useSetterIfExists(proxy, destination, new java.sql.Date(datetime.getMillis()));
        }
    }

    public static void copyToAttachment(DataSetItemAttachment attachmentItem, Object object) {
        ApptreeAttachment apptreeAttachment = (ApptreeAttachment) object;
        attachmentItem.setMimeType(apptreeAttachment.getMimeType());
        attachmentItem.setTitle(apptreeAttachment.getTitle());
        attachmentItem.setFileAttachmentURL(apptreeAttachment.getAttachmentURL());
    }


    public static void copyFromAttachment(DataSetItemAttachment attachmentItem, ApptreeAttachment object) {
        object.setAttachmentURL(attachmentItem.getFileAttachmentURL());
        object.setMimeType(attachmentItem.getMimeType());
        object.setTitle(attachmentItem.getTitle());
    }

    /**
     * @param attributeProxy
     * @param object
     * @param record
     * @param index
     * @param primaryKey
     * @param useGetterAndSetter
     * @param value
     * @param <T>
     * @throws IllegalAccessException
     * @throws InvocationTargetException
     */
    private static <T> void readStringData(AttributeProxy attributeProxy, T object, Record record, int index, boolean primaryKey, boolean useGetterAndSetter, boolean value, boolean parent) throws IllegalAccessException, InvocationTargetException {
        Object fieldData = null;
        if (useGetterAndSetter) fieldData = useGetterIfExists(attributeProxy, object);
        else fieldData = attributeProxy.getValue(object);
        record.setString(fieldData != null ? fieldData.toString() : null, index);
        if (parent) {
            record.setParentValue(fieldData.toString());
        }
        if (value) {
            record.setValue(fieldData.toString());
        }
        if (primaryKey) {
            record.setPrimaryKey(fieldData.toString());
            if (!record.isValueSet()) {
                record.setValue(fieldData.toString());
            }
        }
    }

    /**
     * @param attributeProxy
     * @param object
     * @param record
     * @param index
     * @param primaryKey
     * @param useGetterAndSetter
     * @param value
     * @param <T>
     * @throws IllegalAccessException
     * @throws InvocationTargetException
     */
    private static <T> void readIntegerData(AttributeProxy attributeProxy, T object, Record record, int index, boolean primaryKey, boolean useGetterAndSetter, boolean value, boolean parent) throws IllegalAccessException, InvocationTargetException {
        Integer fieldData = null;
        if (useGetterAndSetter) {
            fieldData = (Integer) useGetterIfExists(attributeProxy, object);
        } else fieldData = (Integer) attributeProxy.getValue(object);
        if (fieldData == null) return;
        record.setInt(fieldData, index);
        if (parent) {
            record.setParentValue(fieldData.toString());
        }
        if (value) {
            record.setValue(fieldData.toString());
        }
        if (primaryKey) {
            record.setPrimaryKey(fieldData.toString());
            if (!record.isValueSet()) {
                record.setValue(fieldData.toString());
            }
        }
    }

    /**
     * @param attributeProxy
     * @param object
     * @param record
     * @param index
     * @param primaryKey
     * @param useGetterAndSetter
     * @param value
     * @param <T>
     * @throws IllegalAccessException
     * @throws InvocationTargetException
     */
    private static <T> void readDoubleData(AttributeProxy attributeProxy, T object, Record record, int index, boolean primaryKey, boolean useGetterAndSetter, boolean value) throws IllegalAccessException, InvocationTargetException {
        String fieldName = attributeProxy.getDataTypeName();
        Double fieldData = null;
        if (fieldName.contains("Float") || fieldName.contains("float")) {
            Float floatValue = null;
            if (useGetterAndSetter) {
                floatValue = (Float) useGetterIfExists(attributeProxy, object);
            } else floatValue = (Float) attributeProxy.getValue(object);
            fieldData = new Double(floatValue);
        } else {
            if (useGetterAndSetter) {
                fieldData = (Double) useGetterIfExists(attributeProxy, object);
            } else fieldData = (Double) attributeProxy.getValue(object);
        }
        record.setDouble(fieldData, index);
        if (value) {
            record.setValue(fieldData.toString());
        }
        if (primaryKey) {
            record.setPrimaryKey(fieldData.toString());
            if (!record.isValueSet()) {
                record.setValue(fieldData.toString());
            }
        }
    }

    /**
     * @param attributeProxy
     * @param object
     * @param record
     * @param index
     * @param primaryKey
     * @param useGetterAndSetter
     * @param value
     * @param <T>
     * @throws IllegalAccessException
     * @throws InvocationTargetException
     */
    private static <T> void readBoolData(AttributeProxy attributeProxy, T object, Record record, int index, boolean primaryKey, boolean useGetterAndSetter, boolean value) throws IllegalAccessException, InvocationTargetException {
        Boolean fieldData = null;
        if (useGetterAndSetter) {
            fieldData = (Boolean) useGetterIfExists(attributeProxy, object);
        } else fieldData = (Boolean) attributeProxy.getValue(object);
        record.setBool(fieldData, index);
        if (value) {
            record.setValue(fieldData.toString());
        }
        if (primaryKey) {
            record.setPrimaryKey(fieldData.toString());
            if (!record.isValueSet()) {
                record.setValue(fieldData.toString());
            }
        }
    }

    /**
     * @param attributeProxy
     * @param object
     * @param record
     * @param index
     * @param primaryKey
     * @param useGetterAndSetter
     * @param value
     * @param <T>
     * @throws IllegalAccessException
     * @throws InvocationTargetException
     */
    private static <T> void readDateData(AttributeProxy attributeProxy, T object, Record record, int index, boolean primaryKey, boolean useGetterAndSetter, boolean value) throws IllegalAccessException, InvocationTargetException {
        DateTime dateTime = getDateValueFromObject(attributeProxy, object, useGetterAndSetter);
        record.setDate(dateTime, index);
        if (value) {
            record.setValue(dateTime.toString());
        }
        if (primaryKey) {
            record.setPrimaryKey(dateTime.toString());
            if (!record.isValueSet()) {
                record.setValue(dateTime.toString());
            }
        }
    }

    /**
     * @param attributeProxy
     * @param object
     * @param record
     * @param index
     * @param primaryKey
     * @param useGetterAndSetter
     * @param value
     * @param <T>
     * @throws IllegalAccessException
     * @throws InvocationTargetException
     */
    private static <T> void readDateTimeData(AttributeProxy attributeProxy, T object, Record record, int index, boolean primaryKey, boolean useGetterAndSetter, boolean value) throws IllegalAccessException, InvocationTargetException {
        DateTime dateTime = getDateValueFromObject(attributeProxy, object, useGetterAndSetter);
        record.setDateTime(dateTime, index);
        if (value) {
            record.setValue(dateTime.toString());
        }
        if (primaryKey) {
            record.setPrimaryKey(dateTime.toString());
            if (!record.isValueSet()) {
                record.setValue(dateTime.toString());
            }
        }
    }

    private static <T> void readLocationData(AttributeProxy proxy, T object, Record record, int index, boolean primaryKey, boolean useGetterAndSetter, boolean value) throws IllegalAccessException, InvocationTargetException {
        Location location = getLocationValueFromObject(proxy, object, useGetterAndSetter);
        record.setLocation(location, index);
        if (value) {
            record.setValue(location.toString());
        }
        if (primaryKey) {
            record.setPrimaryKey(location.toString());
            if (!record.isValueSet()) {
                record.setValue(location.toString());
            }
        }
    }

    /**
     * @param attributeProxy
     * @param object
     * @param useGetterAndSetter
     * @param <T>
     * @return
     * @throws IllegalAccessException
     * @throws InvocationTargetException
     */
    private static <T> DateTime getDateValueFromObject(AttributeProxy attributeProxy, T object, boolean useGetterAndSetter) throws IllegalAccessException, InvocationTargetException {
        List<Class> supportedClasses = getSupportedTypeMap().get(AttributeType.Date);
        if (supportedClasses == null) {
            return new DateTime();
        }
        for (Class clazz : supportedClasses) {
            if (clazz == org.joda.time.DateTime.class && attributeProxy.getType() == clazz) {
                if (fieldHasGetter(attributeProxy, object) && useGetterAndSetter) {
                    return (DateTime) useGetterIfExists(attributeProxy, object);
                } else return (DateTime) attributeProxy.getValue(object);
            }
            if (clazz == java.util.Date.class && attributeProxy.getType() == clazz) {
                if (fieldHasGetter(attributeProxy, object) && useGetterAndSetter) {
                    return new DateTime(((java.util.Date) useGetterIfExists(attributeProxy, object)).getTime());
                } else return new DateTime((Date) attributeProxy.getValue(object));
            }
            if (clazz == java.sql.Date.class && attributeProxy.getType() == clazz) {
                if (fieldHasGetter(attributeProxy, object) && useGetterAndSetter) {
                    return new DateTime(((java.sql.Date) useGetterIfExists(attributeProxy, object)).getTime());
                } else return new DateTime((java.sql.Date) attributeProxy.getValue(object));
            }
        }

        return new DateTime();
    }

    private static <T> void readColorData(AttributeProxy proxy, T object, Record record, int index, boolean useGetterAndSetter) {
        Color color = getColorValueFromObject(proxy, object, useGetterAndSetter);
        record.setColor(color, index);
    }

    /**
     * @param attributeProxy
     * @param object
     * @param dataSetItem
     * @param index
     * @param useGetterAndSetter
     * @param <T>
     * @throws IllegalAccessException
     * @throws InvocationTargetException
     */
    private static <T> void readListItemData(AttributeProxy attributeProxy, T object, Record dataSetItem, int index, boolean useGetterAndSetter) throws IllegalAccessException, InvocationTargetException {
        Object listItemObject = null;
        if (useGetterAndSetter) {
            listItemObject = useGetterIfExists(attributeProxy, object);
        } else listItemObject = attributeProxy.getValue(object);
        ListItem listItem = new ListItem();
        copyToRecord(listItem, listItemObject);
        if (!dataSetItem.isListItem()) {
            dataSetItem.setListItem(listItem, index);
        }
    }

    /**
     * @param attributeProxy
     * @param object
     * @param dataSetItem
     * @param index
     * @param useGetterAndSetter
     * @param <T>
     * @throws IllegalAccessException
     * @throws InvocationTargetException
     */
    private static <T> void readSingleRelationshipData(AttributeProxy attributeProxy, T object, Record dataSetItem, int index, boolean useGetterAndSetter) throws IllegalAccessException, InvocationTargetException {
        Object relationship = null;
        if (useGetterAndSetter) {
            relationship = useGetterIfExists(attributeProxy, object);
        } else relationship = attributeProxy.getValue(object);
        if (relationship == null) {
            return;
        }
        DataSetItem tempItem = dataSetItem.addNewDataSetItem(index);
        copyToRecord(tempItem, relationship);
    }


    /**
     * @param attributeProxy
     * @param object
     * @param dataSetItem
     * @param index
     * @param useGetterAndSetter
     * @param <T>
     * @throws IllegalAccessException
     * @throws InvocationTargetException
     */
    private static <T> void readAttachmentData(AttributeProxy attributeProxy, T object, Record dataSetItem, int index, boolean useGetterAndSetter) throws IllegalAccessException, InvocationTargetException {
        Object relationship = null;
        List<Object> relationships = null;

        if (useGetterAndSetter) {
            if (attributeProxy.isWrappedClass) {
                relationships = (List<Object>) useGetterIfExists(attributeProxy, object);
            } else {
                relationship = useGetterIfExists(attributeProxy, object);
            }
        } else {
            if (attributeProxy.isWrappedClass) {
                relationships = (List<Object>) attributeProxy.getValue(object);
            } else {
                relationship = attributeProxy.getValue(object);
            }
        }

        if (Null(relationship) && !Null(relationships)) {
            RecordUtils.copyListOfAttachmentsToRecordForIndex(relationships, dataSetItem, index);
        }

        if (!Null(relationship) && Null(relationships)) {
            RecordUtils.copySingleAttachmentToRecordForIndex(relationship, dataSetItem, index);
        }

    }


    /**
     * @param attributeProxy
     * @param object
     * @param dataSetItem
     * @param index
     * @param useGetterAndSetter
     * @param <T>
     * @throws IllegalAccessException
     * @throws InvocationTargetException
     */
    private static <T> void readRelationshipData(AttributeProxy attributeProxy, T object, Record dataSetItem, int index, boolean useGetterAndSetter) throws IllegalAccessException, InvocationTargetException {
        List<Object> relationship = null;
        if (useGetterAndSetter) {
            relationship = (List<Object>) useGetterIfExists(attributeProxy, object);
        } else relationship = (List<Object>) attributeProxy.getValue(object);
        if (attributeProxy.useLazyLoad()) {
            dataSetItem.useLazyLoad(index);
        }
        if (Null(relationship)) {
            return;
        }
        for (Object obj : relationship) {
            DataSetItem tempItem = dataSetItem.addNewDataSetItem(index);
            copyToRecord(tempItem, obj);
        }
    }

    private static <T, C extends CustomLocation> Location getLocationValueFromObject(AttributeProxy proxy, T object, boolean useGetterAndSetter)
            throws IllegalAccessException, InvocationTargetException {
        List<Class> supportedClasses = getSupportedTypeMap().get(AttributeType.Location);
        if (supportedClasses == null) return new Location();
        if (proxy.getType() == Location.class) {
            if (fieldHasGetter(proxy, object) && useGetterAndSetter) return (Location) useGetterIfExists(proxy, object);
            Location location = (Location) proxy.getValue(object);
            if (location == null) return new Location();
            Location retLocation = new Location(location.getLatitude(), location.getLongitude());
            retLocation.setAccuracy(location.getAccuracy());
            retLocation.setBearing(location.getBearing());
            retLocation.setElevation(location.getElevation());
            retLocation.setSpeed(location.getSpeed());
            retLocation.setTimestamp(location.getTimestamp());
            return retLocation;
        } else if (CustomLocation.class.isAssignableFrom(proxy.getType())) {
            Location location = new Location();
            C src = (C) proxy.getValue(object);
            if (src == null) return new Location();
            location.setLatitude(src.getLatitude());
            location.setLongitude(src.getLongitude());
            location.setBearing(src.getBearing());
            location.setElevation(src.getElevation());
            location.setSpeed(src.getSpeed());
            location.setAccuracy(src.getAccuracy());
            location.setTimestamp(src.getTimestamp());
            return location;
        }
        return new Location();
    }

    private static <T> Color getColorValueFromObject(AttributeProxy proxy, T object, boolean useGetterAndSetter) {
        if (getSupportedTypeMap().get(AttributeType.Color) == null) return new Color();
        if (proxy.getType() == Color.class) {
            try {
                if (fieldHasGetter(proxy, object) && useGetterAndSetter)
                    return (Color) useGetterIfExists(proxy, object);
                Color objColor = (Color) proxy.getValue(object);
                return new Color(objColor.getR(), objColor.getG(), objColor.getB(), objColor.getA());
            } catch (Exception error) {
            }
        }
        return new Color();
    }


    private static boolean isChildOfApptreeSpecificClass(Class clazz) {
        return (ApptreeAttachment.class.isAssignableFrom(clazz));
    }

    private static AttributeType getAttributeTypeFromApptreeSpecificClass(Class clazz) {
        if (ApptreeAttachment.class.isAssignableFrom(clazz)) {
            return AttributeType.Attachments;
        }
        return AttributeType.None;
    }
}
