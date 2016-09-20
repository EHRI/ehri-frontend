$(document).ready(function () {

  var defaultStartView = "decade";

  var dateField = function (name, src) {
    return "*[name='" + /(.*?)\w+$/.exec(src)[1] + name + "']";
  }

  //Create datepicker
  var createDatepicker = function (target, forced) {
    var process = false,
        override = true,
        hasDP = hasDatepicker(target),
        show = false;

    if (hasDP === true) {
      process = true;
      target.datepicker("remove");
    } else if (typeof forced !== "undefined" && forced === true) {
      process = true;
      override = false;
      show = true;
    }

    if (process === true) {
      target.datepicker({
        startView: defaultStartView,
        format: function () {
          console.log(defaultFormat(target));
          return defaultFormat(target);
        },
        minViewMode: function () {
          return defaultMinViewMode(target);
        }
      });
      target.addClass("datepicker-activated");
      if (show === true) {
        target.datepicker("show");
      }
    }
  }

  var defaultFormat = function (that) {
    name = that.attr("name");
    precision = $(dateField("precision", name)).val();

    switch (precision) {
      case 'month':
      case 'quarter':
        precision = "yyyy-mm";
        break;
      case 'year':
      case 'decade':
        precision = "yyyy";
        break;
      default:
        precision = "yyyy-mm-dd";
        break;
    }
    return precision || "yyyy-mm-dd";
  }

  var defaultMinViewMode = function (that) {
    name = that.attr("name");
    precision = $(dateField("precision", name)).val();

    switch (precision) {
      case 'month':
      case 'quarter':
        precision = 1;
        break;
      case 'year':
      case 'decade':
        precision = 2;
        break;
      default:
        precision = 0;
        break;
    }

    return precision || false;
  }

  var hasDatepicker = function (obj) {
    return obj.hasClass("datepicker-activated") !== false;
  }

  //jQuery Handler
  $(document).on("change", ".precision", function () {
    var that = $(this),
        start = $(dateField("startDate", that.attr("name"))),
        end = $(dateField("endDate", that.attr("name")));

    createDatepicker(start);
    createDatepicker(end);
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
        createDatepicker(start);
        createDatepicker(end);
      }
    }
  });

  $(document).on('click', '.datepicker-activation', function () {
    var name = $(this).attr("data-target");
    target = $(name);
    createDatepicker(target, true);
  });
});
