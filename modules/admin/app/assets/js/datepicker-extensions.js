$(document).ready(function () {

  // luxon.Settings.throwOnInvalid = true;

  function dateField(name, src) {
    return "*[name='" + /(.*?)\w+$/.exec(src)[1] + name + "']";
  }

  //Create datepicker
  function createDatePicker(target, forced) {
    var process = false,
        hasDP = hasDatepicker(target),
        show = false;

    if (hasDP === true) {
      process = false;
      target.datepicker("destroy");
      target.removeClass("datepicker-activated");
    } else if (typeof forced !== "undefined" && forced) {
      process = true;
      show = true;
    }

    if (process === true) {
      target.datepicker({
        startView: defaultMinViewMode(target),
        immediateUpdates: true,
        format: {
          toDisplay: function(date, format) {
            return luxon.DateTime.fromJSDate(date).toFormat(defaultFormat(target));
          },
          toValue: function (date, format) {
            return luxon.DateTime.fromISO(date).toJSDate();
          },
        },
        minViewMode: defaultMinViewMode(target),
      });
      target.addClass("datepicker-activated");
      if (show === true) {
        target.datepicker("show");
      }
    }
  }

  function defaultFormat(that) {
    var name = that.attr("name");
    var precision = $(dateField("precision", name)).val();

    switch (precision) {
      case 'month':
      case 'quarter':
        precision = "yyyy-MM";
        break;
      case 'year':
      case 'decade':
        precision = "yyyy";
        break;
      default:
        precision = "yyyy-MM-dd";
        break;
    }
    return precision || "yyyy-MM-dd";
  }

  function defaultMinViewMode (that) {
    var name = that.attr("name");
    var precision = $(dateField("precision", name)).val();

    console.log("Precision min view mode", precision)
    switch (precision) {
      case 'month':
      case 'quarter':
        return 1;
      case 'year':
      case 'decade':
        return 2;
      default:
        return 0;
    }
  }

  function hasDatepicker(obj) {
    return obj.hasClass("datepicker-activated") !== false;
  }

  //jQuery Handler
  $(document).on("change", ".precision", function () {
    var that = $(this),
        start = $(dateField("startDate", that.attr("name"))),
        end = $(dateField("endDate", that.attr("name")));

    createDatePicker(start);
    createDatePicker(end);
  });

  $(document).on("keyup", "input.datepicker", function () {
    var that = $(this),
        val = that.val(),
        name = that.attr("name"),
        precision = "day",
        table = [],
        select = dateField("precision", name),

        start = $(dateField("startDate", that.attr("name"))),
        end = $(dateField("endDate", that.attr("name")));

    //If the input has some val and it's bigger than 4 (year is minimum scope), we can start
    if (typeof val.length !== "undefined" && val.length >= 4) {
      table = val.split("-");
      if (typeof table[1] !== "undefined") {
        if (table[1] === "" || table[1] === "-") {
          table = [table[0]];
        }
      }
      if (table.length > 0) { //Checking for bug
        switch (table.length) {
          case 2:
            precision = "month";
            break;
          case 1:
            precision = "year";
            break;
          default:
            precision = "day";
            break;
        }

        //Set the precision field
        $(select + " > option[value='" + precision + "']").prop('selected', true);
        //Restart the datepicker
        createDatePicker(start);
        createDatePicker(end);
      }
    }
  });

  $(document).on('click', '.datepicker-activation', function () {
    var name = $(this).attr("data-target");
    createDatePicker($(name), true);
  });
});
