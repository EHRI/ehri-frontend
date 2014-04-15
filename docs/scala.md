# Scala Notes

## Coding Guidelines

Since Scala is quite syntactically flexible this app uses a couple of rules designed to a) cut down the number of trivial decisions necessary when writing every-day code, and b) not scaring Java developers too much.

### Avoid infix notation for method calls

The ability to call methods on objects using infix notation, is nice and can reduce the amount of visual noise. However, it can also be a bit unfamiliar (and hence scary) to those more familiar with Java-like "dot-method" syntax, therefore instead of doing this:

```scala
myList filter someFunc map anotherFunc toList
```

prefer

```scala
myList.filter(someFunc).map(anotherFunc).toList
```

## Magic

Scala is a language that provides some very powerful abstraction facilities that can greatly reduce boilerplate, thus helping the actually important stuff stand out. Many of these facilities derive from the various forms of `implicit` constructs which, despite sharing a name, often have quite different uses. The downside with "implicit" stuff is that it can often appear magical to those not familiar with exactly what's happening (i.e. anyone not already well-versed in Scala.) In this app we try and steer clear of excessive magic, however implicits are used in several different ways:

### Implicit classes and extension methods

Scala allows you to "extend" existing classes using implicit wrapper classes. We try and avoid this pattern because it can be confusing when you see a method being called on an object that doesn't appear to be defined in that object's class. Exceptions to this general rule, however, exist in the following places:

 - The `RequestHeader` class is extended with a `preferences` method that extracts a preferences object of type `T` from the current session. The extension method is defined in `SessionPreferences.scala` and will be added to `RequestHeader` in any class that extends the `SessionPreferences` trait with preferences type `T`.

 - The `Result` class is extended with a `withPreferences(prefs: T)` method that allows serializing a preferences object of type `T` to the current session. The extension method is defined in `SessionPreferences.scala` and will be added to `Result` in any class that extends the `SessionPreferences` trait with preferences type `T`.

An example of this mechanism is available in the `SessionPreferencesSpec.scala` test class.

### Implicit method parameters

An implicit parameter is one that the Scala compiler automatically adds to method calls if it can find an object of the appropriate type in the current scope (though the rules of where it searches for implicit parameters is a bit [complex](http://stackoverflow.com/a/5598107/285374).)

We use implicit parameter lists in several places, most notably in templates. Nearly every time we render a template in the app there are several bits of context it needs:

 - the current request
 - the current (optional?) user
 - the current language
 - the current flash (data that displays on the *next* page render only)
 - global application configuration

Since there are hundreds of calls to render templates functions in this app, specifying all these parameters manually would be exceedingly tediuous. For this reason these parameters are almost always in the implicit part of the template argument list so the compiler will fill them in for us. In a typical controller action which renders a template the various context objects come from the following places:

 - request: In a Play `Action` the incoming request is always marked as `implicit`
 - optional user: This is available if we use the `optionalUserProfile` wrapper for `Action` (which is almost always the case)
 - language: This is implicitly available **if** there is an implicit request in scope, via a method defined on the Play `Controller` class
 - flash: ditto
 - global app config: This is injected into all controller classes via the app `Global` object

#### Additional use of implicit methods

Implicits are also used in the following places:

 - An `ApiUser` is required for all backend data calls, and is usually derived from an in-scope `UserProfile` via a method in `ControllerHelpers.scala`
 - Backend calls which fetch parametrised data types there's nearly always an implicit `Readable[T]` or `Writable[T]` in scope which handles serializing and deserializing the type from the backend's JSON data.
