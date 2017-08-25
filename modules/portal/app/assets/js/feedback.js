// Handle slide-out feedback form
//

jQuery(function ($) {
  // handle feedback form submission...
  var $container = $(".feedback-container"),
      $toggle = $("#feedback-toggle", $container),
      $form = $(".feedback-form", $container),
      $submit = $(".feedback-submit", $container),
      $thanks = $(".feedback-thanks", $container),
      $email = $("input[name='email']", $form),
      $text = $("textarea[name='text']", $form);

  $form.validate({
    showErrors: function (em, el) {
    }
  });

  $submit.prop("disabled", true);

  $email.on("blur", function (event) {
    $email.parent(".form-group")
        .toggleClass("has-error", !$email.valid());
  });

  $form.on("keyup", function (event) {
    $submit.prop("disabled", !$form.valid());
  });

  $form.submit(function (event) {
    event.preventDefault();
    $submit.prop("disabled", true);
    $.post($form.attr("action"), $form.serialize(), function (data) {
      $thanks.slideDown(500, function () {
        setTimeout(function () {
          $toggle.dropdown("toggle");
          $text.val("");
          $thanks.hide();
        }, 1000);
      });
    });
  });
});