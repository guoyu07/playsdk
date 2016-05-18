import 'package:polymer/polymer.dart';

import 'package:sdkwebvalidator/models/models.dart';

/// Represents an item in a [Form].
/// Contains helper methods for getting and setting the runtime value (ints, bools)
class FormElementDisplay extends JsProxy {
  DisplayElement formElement;
  String _value;

  FormElementDisplay(this.formElement, [this._value = '']);

  String get value => _value;
  void set value(String value) {
    _value = value;
  }

  @reflectable
  String get title {
    return formElement.title;
  }

  int intValue() {
    return int.parse(_value);
  }

  void setIntValue(int intValue) {
    _value = intValue.toString();
  }

  bool boolValue() {
    return _value?.toLowerCase() == "Y" ?? false;
  }

  void setBoolValue(bool boolValue) {
    this._value = boolValue ? "Y" : "N";
  }

  bool get hidden {
    return formElement.hidden;
  }

  /// The Polymer template to use for this form element.
  String get templateName {
    if (formElement.hidden) {
      return "at-hidden";
    }
    switch (formElement.displayType) {
      case DisplayType.TextField:
        return "at-textfield";
      case DisplayType.SelectList:
        return "at-select-list";
      case DisplayType.Relationship:
        return "at-relationship";
      default:
        return "at-hidden";
    }
  }
}
