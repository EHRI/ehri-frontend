jQuery(function($) {

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
  $("input[title!=''],textarea[title!='']").each(function() {
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
  $(".chzn-select").chosen();

  // Fade success flash message after 3 seconds
  $(".success-pullup").fadeOut(3000);

  // Delete inline date period tables
  $(".inline-element-set").on("click", ".remove-inline-element", function(event) {
    $(this).closest(".inline-element").remove();
    event.preventDefault();
  });

  $(".inline-element-set").on("click", ".add-inline-element", function(event) {
    var container = $(event.target).closest(".inline-element-set");
    var template = $(".inline-element-template", container);
    var idx = $(".inline-element", container).length;
    var elem = $(template.html().replace(/IDX/g, idx));
    container.append(elem);
    event.preventDefault();
  });

});