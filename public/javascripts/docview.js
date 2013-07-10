jQuery(function($) {

  /**
   * Description viewport code. This fixes a viewport to a list
   * of item descriptions so only the selected one is present
   * at any time.
   */
  $(window).hashchange(function() {
    var hash = location.hash;

    $(".description-viewport").each(function(i, elem) {

      var $vp = $(elem);
      var $descs = $vp.find(".description-holder");

      // If the hash isn't set, default to the first element
      if (!hash) {
        hash = "#" + $descs.first().attr("id");
      }

      var $theitem = $(hash, $vp);

      $theitem.show();
      $descs.not($theitem).hide();

      // Set the active class on the current description
      $(".description-switch[href='" + hash + "']").parent().addClass("active")
      $(".description-switch[href!='" + hash + "']").parent().removeClass("active")
    });
  });

  // Trigger a change on initial load...
  $(window).hashchange();

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

  // Chosen selects - makes multi-select pretty
  $(".chzn-select:visible").chosen();

  // Fade success flash message after 3 seconds
  $(".success-pullup").fadeOut(3000);

  // Delete inline date period tables
  $(".remove-inline-element").live("click", function(event) {
    $(this).closest(".inline-element").remove();
    event.preventDefault();
  });

  $(".add-inline-element").live("click", function(event) {
    var container = $(event.target).closest(".inline-formset");
    var set = container.children(".inline-element-list");
    var template = $(".inline-element-template", container);
    var idx = set.children().length;
    var elem = $(template.html().replace(/IDX/g, idx));
    //container.append(elem);
    set.append(elem);

    // Add chosen support to loaded content...
    elem.find(".chzn-select").chosen();
    event.preventDefault();
  });

});