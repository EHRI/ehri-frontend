// Handle slide-out feedback form
//

jQuery(function($) {
  // handle feedback form submission...
  var $handle = $(".feedback-handle"),
      container = $(".feedback"),
      $cancel = $(".feedback-cancel", container),
      $form = $(".feedback-form", container),
      $submit = $(".feedback-submit", container),
      $close = $(".feedback-close", container),
      $thanks = $(".feedback-thanks", container),
      $email = $("input[name='email']", $form),
      $text = $("textarea[name='text']", $form);

  // Trigger closing of the feedback form when clicking
  // outside its container, or on or inside the close
  // trigger.
  var feedbackOut = function(e) {
    if($(e.target).parents(".feedback-container").length != 1
        || $(e.target).hasClass("feedback-close")
        || $(e.target).parent().hasClass("feedback-close")) {
      $handle.trigger("click");
    }
  };

  $handle.on("click", function(e) {
    e.preventDefault();
    // Add a close button when the form becomes
    // visible. Remove it on hiding.
    container.toggle(300, function() {
      if(container.is(":visible")) {
        $form.append(
          $("<a />", {
            "class" : "feedback-close",
            "html" : "<span class='glyphicon glyphicon-remove'></span>"
          })
        );
        $(document).bind("click", feedbackOut);
      } else {
        $(document).unbind("click", feedbackOut);
        container.find(".feedback-close").remove();
      }
    });
  });

  $form.validate({
    showErrors: function(em, el) {}
  });

  $submit.prop("disabled", true);

  $email.on("blur", function(event) {
    $email.parent(".form-group")
        .toggleClass("has-error", !$email.valid());
  });

  $form.on("keyup", function(event) {
    $submit.prop("disabled", !$form.valid());
  });

  $close.on("click", function() {
    $handle.trigger("click");
  });

  $cancel.on("click", function(e) {
    e.preventDefault();
    $text.val("");
    $handle.trigger("click");
  });

  $submit.on("click", function(event) {
    event.preventDefault();
    $submit.prop("disabled", true);
    $.post($form.attr("action"), $form.serialize(), function(data, textStatus) {

      // Hide the form and show the thanks message...
      $form.hide(100, function() {
        $thanks.slideDown(500, function() {
          setTimeout(function() {
            $text.val("");

            $thanks.slideUp(500, function() {
              $handle.trigger("click").queue(function(next) {
                container.find("form").show();
                next();
              });

            });
          }, 1000);
        });
      });
    });
  });
});