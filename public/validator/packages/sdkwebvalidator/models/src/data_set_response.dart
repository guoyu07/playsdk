part of sdkwebvalidator.models;

class DataSetResponse extends Object with JsProxy {
  @reflectable
  final bool success;

  @reflectable
  final String message;

  @reflectable
  final bool showMessageAsAlert;

  @reflectable
  final int totalRecords;

  @reflectable
  final int numberOfRecords;

  @reflectable
  final bool moreRecordsAvailable;

  @reflectable
  List<DataSetItem> dataSetItems;

  DataSetResponse(
      this.success,
      this.message,
      this.showMessageAsAlert,
      this.totalRecords,
      this.numberOfRecords,
      this.moreRecordsAvailable,
      this.dataSetItems);

  factory DataSetResponse.fromJson(json) {
    var dataSetItems = (json['records'] as List)?.map((d) {
      if (d == null) return null;
      var item = new DataSetItem();
      item.updateFromJson(d, "");
      return item;
    })?.toList();

    return new DataSetResponse(
        json['success'],
        json['message'],
        json['showMessageAsAlert'],
        json['totalRecords'],
        json['numberOfRecords'],
        json['moreRecordsAvailable'],
        dataSetItems);
  }

  Map<String, dynamic> toJson() => <String, dynamic>{
        'success': success,
        'message': message,
        'showMessageAsAlert': showMessageAsAlert,
        'totalRecords': totalRecords,
        'numberOfRecords': numberOfRecords,
        'moreRecordsAvailable': moreRecordsAvailable,
        'records': dataSetItems
      };
}
