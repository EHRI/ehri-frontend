jQuery(function($) {
  /*
  *   Quiet mode by Thibault Clérice GitHub@PonteIneptique
  */
  $(".form-horizontal .form-group").each(function(e) {
    var $textarea = $(this).find("textarea");
    var $formgroup = $(this);
    if($textarea.length !== "undefined" && $textarea.length === 1 && !$formgroup.hasClass("inline-formset")) {
      $textarea.parent().append('<div><a href="#" class="quiet-toggle"><span class="glyphicon glyphicon-remove"></span><span class="glyphicon glyphicon-fullscreen"></span><span class="Press Esc or Tab to continue"></span></a></div>');
    }
  });
  $(".form-group").on("click", ".quiet-toggle", function(e) {
    $(this).trigger("quiet-toggle")
  });

  $(".form-group").on("quiet-toggle", ".quiet-toggle", function(e) {
    e.preventDefault();
    var $formgroup = $(this).parents(".form-group");
    var $blockhelp = $formgroup.find(".help-block");
    $formgroup.toggleClass("quiet");
    if($formgroup.hasClass("quiet")) {
      $blockhelp.data("html", $blockhelp.html()).html($(".markdown-cheatsheet").html());
      $formgroup.find("textarea").focus();
    } else {
      $blockhelp.html($blockhelp.data("html"));
    }
  });

  $(".form-group").on("keydown", function(e) {
    var $formgroup = $(this);
    if($formgroup.hasClass("quiet")) {
      if (e.keyCode == 27) {
          e.preventDefault();
          $formgroup.find(".quiet-toggle").trigger("quiet-toggle");
      } else if (e.keyCode == 9) {
        e.preventDefault();
        $formgroup.find(".quiet-toggle").trigger("quiet-toggle");

        var $next = $formgroup.nextAll(".form-group:has(.quiet-toggle)").first();
        if($next.length !== "undefined" && $next.length === 1) {
          $next.find(".quiet-toggle").trigger("quiet-toggle");
        } else {
          var $fieldset = $formgroup.parents("fieldset");
          $fieldset.nextAll("fieldset").each(function (e) {
            var $next = $(this).find(".form-group:has(.quiet-toggle)").first();
            if($next.length !== "undefined" && $next.length === 1) {
              $next.find(".quiet-toggle").trigger("quiet-toggle");
              console.log(true);
              return false;
            }
          });
        }
      }
    }
  });

  /*
  * End of quiet mode
  */
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
          if (!that.isSticky === sticky || ($(window).outerHeight() != that.attr("data-windows"))) {
            that.css({
              position: "fixed",
              left: 0,
              width: $(window).width(),
              top: $(window).height() - height// - 15 // Unfortunate fudge factor!
            }).addClass("sticky").attr("data-windows", $(window).outerHeight());
            that.isSticky = sticky;
          }
        }
        else {
          if (!that.isSticky === sticky) {
            that.removeAttr("style")
                .removeClass("sticky");
            that.isSticky = null;
          }
        }
      }

      $(window).scroll(shouldStick);
      $(window).on('resize', shouldStick);

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
      if(!that.parents(".control-group").hasClass("quiet")) {
        that.popover({
          html: true,
          delay:{
            show: 500,
            hide: 100
          },
          trigger: "blur",
          placement: "bottom"
        });
      }
  });

  /**
   * Quick and dirty way of Ajax-ing a link which
   * directs to an empty submit form. Note: this
   * does not work well for deletes because it simply
   * refreshes the page after success (and in that case
   * the item will have been deleted, resulting in a 404).
   */
  $("a.ajax-action").click(function(e) {
    var $link = $(this),
        href = $link.attr("href"),
        check = $link.attr("title");
    e.preventDefault();
    if (confirm(check)) {
      $.post(href, function(d) {
        location.reload();
      })
    }
  });

  // Make multi-selects pretty
  $("select.select2:not(.inline-element-template select.select2)").select2();


  // Fade success flash message after 3 seconds
  $(".success-pullup").fadeOut(3000);

  // Delete inline date period tables
  $(".inline-element-list").on("click", ".remove-inline-element", function(event) {
    event.preventDefault();
    $(this).parents(".inline-element").first().remove();
  });

  $(".add-inline-element").on("click", function(event) {
    event.preventDefault();
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
    elem.find("div.select2-container").remove()
    elem.find("select.select2").removeClass(".select2-offscreen").select2();
  });

});