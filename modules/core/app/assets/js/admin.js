jQuery(function($) {
  /**
   * Markdown helper
   */

   $(".markdown-helper").popover({
      html: true,
      placement: "left",
      content : function () {
        return $(".markdown-cheatsheet").html();
      }
    });
   
  /**
   * jQuery plugin that makes an element 'stick' to the bottom
   * of the viewport if it is outside. Used for form action
   * sections containing the submit button.
   */
  $.fn.stickyFormFooter = function() {
    if(this.length > 0) {
      var that = this;
      var top = that.offset().top;
      var height = that.outerHeight();
      var innerHeight = that.height();

      function shouldStick() {
        var vpend = $(window).outerHeight() + $(window).scrollTop();
        var sticky = top > vpend + innerHeight - height;

        if (sticky) {
          if (!that.isSticky === sticky) {
            that.css({
              position: "fixed",
              left: 0,
              width: $(window).width(),
              top: $(window).height() - height - 15 // Unfortunate fudge factor!
            }).addClass("sticky");
            that.isSticky = sticky;
          }
        } else {
          if (!that.isSticky === sticky) {
            that.removeAttr("style")
                .removeClass("sticky");
            that.isSticky = null;
          }
        }
      }

      $(window).scroll(shouldStick);
      shouldStick();
    }
  }

  $(".form-actions").stickyFormFooter();

  $("nav.responsive").stickyFormFooter();

  // Add Bootstrap tooltip on input boxes with a title.
  // Filter items with an empty title.
  $("input[type=text][title!=''],textarea[title!='']").each(function() {
      var that = $(this);
      that.attr("data-content", that.attr("title"));
      that.attr("title", that.parents(".control-group").find(".control-label").text());
      that.popover({
        html: true,
        delay:{
          show: 500,
          hide: 100
        },
        trigger: "blur",
        placement: "right"
      });
  });

  // Make multi-selects pretty
  $("select.select2").select2();


  // Fade success flash message after 3 seconds
  $(".success-pullup").fadeOut(3000);

  // Delete inline date period tables
  $(".remove-inline-element").on("click", function(event) {
    $(this).closest(".inline-element").remove();
    event.preventDefault();
  });

  $(".add-inline-element").on("click", function(event) {
    var container = $(event.target).closest(".inline-formset");
    var set = container.children(".inline-element-list");
    var prefix = container.data("prefix");
    if (!prefix) {
      throw "No prefix found for formset";
    }
    var template = $(".inline-element-template", container);
    var idx = set.children().length;
    // We want to replace all instances of prefix[IDX] and prefix_IDX
    var re1 = new RegExp(prefix + "\\[IDX\\]", "g");
    var re2 = new RegExp(prefix + "_IDX", "g");
    var elem = $(template.html()
        .replace(re1, prefix + "[" + idx + "]")
        .replace(re2, prefix + "_" + idx));
    //container.append(elem);
    set.append(elem);

    // Add select2 support...
    elem.find("select.select2").select2();
    event.preventDefault();
  });

});