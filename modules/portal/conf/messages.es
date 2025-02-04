welcome=Bienvenido al portal del EHRI
welcome.title=Bienvenido al portal en línea del European Holocaust Research Infrastructure
home=Inicio
about=Sobre EHRI
language=Idioma
contact=Contacto
welcome.blurb=El portal del EHRI ofrece acceso a información sobre la documentación de archivos relevante para el Holocausto, convervada en instituciones de dentro y fuera de Europa. Para más información sobre el proyecto EHRI, visite https://ehri-project.eu.
welcome.video.heading=Introdución en 3 minutos al portal del EHRI
footerText=Esta es una versión de desarollo de la interfaz del portal del EHRI.
footer.funding=El proyecto EHRI está financiado por la Comisión Europea
close=Cerrar
optional=Opcional
cancel=Cancelar
warning=Advertencia
admin.site=Sitio de administración
browse=Explorar
details.show=Mostrar detalles del elemento
details.hide=Ocultar detalles del elemento
project.site=Página web del proyecto EHRI
fullscreen.viewOnPortal=Ver este elemento en el portal del EHRI
mailingList.prompt=Manténgase al día de las noticias del EHRI
mailingList.subscribe=Subscribirse a la lista de correo

#
# Help https://portal.ehri-project.eu (Help menu)
#
help=Ayuda
help.manual=Manual en línea
help.faq=Preguntas Frequentes
help.contact=Contactar al EHRI
help.dataModel=Modelo de datos

#
# Landing page: https://portal.ehri-project.eu
#
landingBlurb.Country=Explorar {0}.
landingBlurb.Country.link={0} informes de país
landingBlurb.Repository=Explorar {0} en {1}.
landingBlurb.Repository.link={0} archivos
landingBlurb.Repository.Country.link={0} países
landingBlurb.DocumentaryUnit=Explorar {0} en {1}.
landingBlurb.DocumentaryUnit.link={0} descripciones archivísticas
landingBlurb.DocumentaryUnit.Repository.link={0} archivos
landingBlurb.AuthoritativeSet=Explorar registros de autoridades
landingBlurb.CvocVocabulary=Explorar términos y lugares.


#
# Form constraints, errors, and formats
#
constraints.mandatory=Obligatorio
constraints.desirable=Recomendable
constraints.timeCheckSeconds=Marca de tiempo
constraints.timeCheckSeconds.failed=El formulario fue enviado muy rápido. Por favor, inténtelo de nuevo.
constraints.honeypot=Carta blanca
constraints.honeypot.failed=El formulario fallo la verificación de SPAMThe form failed the spam check. Por favor, inténtelo de nuevo.
constraints.uniqueness=Debe ser único
constraints.uniqueness.failed=Un elemento con este valor ya existe

format.boolean=Activado/Desactivado

errors.staffOnly=Restringido
errors.staffOnlyMessage=El acceso a esta página está actualmente restringido a personal del EHRI.
errors.verifiedOnly=Verifación por correo electrónico requerida
errors.verifiedOnlyMessage=El acceso a este recurso está restringido a los usuarios con una dirección de correo electrónico verificada. Haga click en el botón inferior para recibir un correo de verificación en su dirección de correo electrónico. Asegúrse de verificar también su carpeta de SPAM si no recibe el correo.
errors.readonly=El portal del EHRI está actualmente disponible en modo sólo de lectura mientras llevamos a cabo las tareas de mantenimiento.El servicio habitual será ser restablecido a su debido tiempo. Gracias por su paciencia.
errors.itemNotFound=404 Recurso no encontrado
errors.itemNotFound.explanation=No se ha podido encontrar ninguna página en esta dirección.
errors.gone=410 Este recurso ya no está disponible
errors.gone.explanation=Esta página fue borrada el {0,date}. Este puede suceder debido a que un proveedor de contenido haya borrado un recurso de su propia base de datos, o por otras razones relacionadas al mantenimiento.
errors.pageNotFound=404 Recurso no encontrado
errors.pageNotFound.explanation=La página en esta dirección no existe o ha sido trasladada.
errors.pageNotFound.search=Intente una búsqueda de un elemento específico:
errors.pageNotFound.links=...o estos enlaces para más ayuda:
errors.clientError=Ha ocurrido un error
errors.genericProblem=Ha ocurrido un error
errors.genericProblem.explanation=Lo sentimos, pero ha habido un error al cargar esta página. Hemos sido informados de este incidente y haremos lo posible para arreglar este problema lo antes posible.
errors.maintenance=Sitio web temporalmente no disponible
errors.maintenance.explanation=El portal del EHRI no está actualmente disponible por razones de mantenimiento. Por favor, vuelva a intentarlo en unos minutos.
errors.databaseError=Error en la base de datos
errors.databaseError.explanation=Parece que nuestra base de datos no está disponible. Esto se trata normalmente de un incidente temporal, por favor, vuelva a intentarlo de nuevo en unos minutos.
errors.searchEngineError=Error en el motor de búsqueda
errors.searchEngineError.explanation=Parece que nuestro motor de búsqueda no está disponible. Esto se trata normalmente de un incidente temporal, por favor, vuelva a intentarlo de nuevo en unos minutos.
errors.databaseMaintenance=Base de datos temporalmente no disponible
errors.databaseMaintenance.explanation=La base de datos del EHRI no esta disponible actualmente por razones de mantenimiento. La base de datos debería estar de nuevo en unos minutos.
errors.permissionDenied=Permiso denegado
errors.permissionDenied.explanation=Parece que que no puedes hacer lo que estabas intendo hacer. Probablemente se trate de nuestro error e intentaremos arreglarlo lo más rápido posible.
errors.errorDetails=Detalles
errors.noFurtherInfo=No hay información adicional disponible.
errors.imageTooLarge=La imagen es muy grande. Por favor, seleccione una imagen de un tamaño inferior a 5 megabytes.
errors.imageResolutionTooLarge=Esta imagen es muy grande. Por favor, seleccione una con un número total de píxeles menor.
errors.badFileType=El archivo cargado no contiene un tipo de imagen soportado.
errors.noFileGiven=No ha seleccionado ningún archivo de imagen.
errors.invalidUrl=La dirección web no es válida
errors.sso=Error de autenticación
errors.sso.notEnabled=La autenticación Single sign-on no está disponible en este sitio.
errors.sso.badData=Imposible validar los datos del single sign-on.

identifier=Identificador
identifier.description=El idenficador de un elemento es unico en el contexto de su elemento padre. El idenficiador del tiem determina su URL y normalmente nunca debería ser cambiado una vez ya creado.

#
# Additional Language codes that aren't official ISO639-2
#
languageCode.mul=Multiple
languageCode.lad=Ladino
languageCode.sh=Serbocroata

#
# Additional/alternate country codes
#
countryCode.xk=Kosovo
countryCode.mk=Macedonia del Norte

#
# Pagination etc
#
# Pagination display. Should say something like "Displaying items 1 to 20 of 25.
# If there are 0 items, says "No items found", one item, "One item found" etc.
pagination.displayingItems={2,choice,0#Ningún elemento encontrado|1#Un elemento encontrado|1<Mostrando los elementos desde {0,number,integer} hasta {1,number,integer} de un total de {2,number,integer}}
pagination.nextPage=Siguiente página
pagination.previousPage=Página anterior
truncated=...
truncated.items.remaining={0,choice,0#|1#y uno más...|1<and {0,number,integer} más...}

#
# Downloading and formats
#
download=Descargar
download.format.txt=Descargar en texto plano
download.format.csv=Descargar en CSV
download.format.tsv=Descargar en TSV
download.format.json=Descargar en JSON

export=Export
export.metadata=Exportar metadatos
export.format.json=JSON
export.format.eag=EAG 2012 XML
export.format.ead=EAD 2002 XML
export.format.ead3=EAD-3 XML
export.format.eac=EAC 2010 XML
export.format.ttl=TTL
export.format.rdf_xml=RDF/XML

#
# Profile https://portal.ehri-project.eu/profile (and related pages)
#
profile.preferences=Preferencias
profile.preferences.updated=Preferencias actualizadas
profile.preferences.allowMessaging=Permitir que otros usuarios le envíen mensajes.
profile.preferences.allowMessaging.description=Tu dirección de correo electrónico no será directamente visible.
profile.preferences.updatePreferences=Acutalizar preferencias
profile.preferences.view=Consultar preferencias
profile.preferences.view.showUserContent=Mostrar notas públicas de otros usuarios

profile=Perfil
profile.section.general=Sobre ustedAbout you
profile.section.image=Foto de perfil
profile.section.preferences=Preferencias de la cuenta
profile.section.email=Correo electrónico
profile.section.password=Contraseña
profile.section.delete=Borrar cuenta
profile.name=Su nombre
profile.location=Ubicación
profile.location.description=Lugar donde usted trabaja o vive
profile.url=Página web personal
profile.url.description=La dirección de su sitio web
profile.workUrl=Página web institucional
profile.workUrl.description=La dirección a su página dentro de la página web de su institución
profile.institution=Institución
profile.institution.description=Su afiliación institucional
profile.interests=Área o áreas de interes
profile.title=Título
profile.role=Rol
profile.languages=Idiomas
profile.about=Sobre
profile.menu.link=Ir a su perfil
profile.edit=Editar perfil
profile.image.edit=Establecer foto de perfil
profile.delete=Borrar cuenta
profile.delete.link=Haga click aquí para borrar su perfil
profile.delete.check.text=Borrar su cuenta anonimizará (pero no borrará) cualquier anotación publica o enlace que haya hecho como contribución al EHRI. Para confirmar, por favor, introduzca su nombre, en el cuadro de abajo, tal como se muestra aquí:
profile.delete.check=Introduzca su nombre como se muestra arriba.
profile.delete.badConfirmation=El borrado de la cuenta no se ha confirmado correctamente.
profile.profile.delete.confirmation=Su perfil ha sido borrado.
profile.update=Actualizar perfil
profile.update.submit=Actualizar perfil
profile.update.confirmation=Perfil correctamente actualizado
profile.menu=Menú del perfil
profile.orcid.connect=Vincule su ORCID
profile.orcid.connected=Su ORCID está vinculado a su cuenta del EHRI.
profile.orcid.connect.info=Vincule su ORCID a su perfil del EHRI para mostrarlo en su perfil.
profile.orcid.connect.submit=Vincular ORCID
profile.orcid.disconnect=Desvincular su ORCID.
profile.orcid.disconnect.info=Eliminar su ORCID de su perfil del EHRI.
profile.orcid.disconnect.submit=Desvincular ORCID
profile.orcid.disconnected=Su ORCID ha sido eliminado de su perfil del EHRI.
profile.orcid.icon=El icono de ORCID
profile.orcid.view=Ver perfil ORCID de {0}


profile.watch.list=Elementos seguidos
profile.watch.by={0} - Elementos seguidos
profile.watch=Seguir elemento
profile.watch.title=Añadir elemento a su lista de seguimiento
profile.watch.tooltip=Haga click para ver las actualizaciones de este elemento en su lista de actividades.
profile.watch.submit=Seguir elemento
profile.watch.search=Buscar elementos seguidos...
profile.unwatch=Dejar de seguir elemento
profile.unwatch.tooltip=Haga click para dejar de recibir actualizaciones de este elemento en su lista de actividades.
profile.unwatch.submit=Dejar de seguir elemento
profile.unwatch.title=Eliminar elemento de su lista de seguimiento

#
# Data types
#
type=Tipo de elemento
type.Repository=Archivos
type.RepositoryDescription=Archivos
type.Repository.description=Un inventario de archivos que mantienen documentación relacionada con el Holocausto.
type.DocumentaryUnit=Descripciones archivísticas
type.DocumentaryUnitDescription=Descripciones archivísticas
type.DocumentaryUnit.description=Descripciones electrónicas e instrumentos de descripción de la documentación de archivo relacionada con el Holocausto.
type.HistoricalAgent=Registros de autoridad
type.HistoricalAgentDescription=Registros de autoridad
type.HistoricalAgent.description=Personas, familias e instituciones relacionadas a las descripciones archivísticas del EHRI.
type.Country=Países
type.Country.description=Informes nacionales del EHRI que proveen un resumen de la historia de la Segunda Guerra Mundial y el Holocausto, así como de la situación de los archivos en los países incluidos en el portal.
type.CvocConcept=Palabras clave
type.CvocConceptDescription=Palabras clave
type.CvocConcept.description=Términos temáticos relacionados con las descripciones archivísticas del EHRI.
type.UserProfile=Personas en el EHRI
type.Group=Grupos
type.VirtualUnit=Colecciones virtuales
type.Annotation=Notas
type.Link=Conexiones
type.CvocVocabulary=Vocabularios
type.CvocVocabulary.description=Términos o lugares relacionados con las descripciones archivísticas.
type.AuthoritativeSet=Registros de autoridad
type.AuthoritativeSet.description=Personas e instituciones relacionadas con las descripciones archivísticas.

#
# Dates
#
dates.start.year=Año de comienzo
dates.end.year=Año de fin
dates.exact={0,number,#}
dates.before=Antes {0,number,#}
dates.between={0,number,#} a {1,number,#}
dates.after=Después {0,number,#}
dates.all=Todo


#
# Login-related
#
login=Iniciar sesión
login.title=Iniciar sesión o registrarse para obtener una cuenta en el EHRI
login.benefits=Una cuenta en el EHRI le permite:
login.benefits.1=Establacer contacto con otra gente dentro del EHRI
login.benefits.2=Seguir las actividades y los cambios en la documentación que sean de su interés
login.benefits.3=Escribir notas y ayudar a mejorar los datos del EHRI
login.signup=Registrarse
login.signup.prompt=¿No dispone de una cuenta? {0}
login.signup.prompt.text=Registrarse
login.login.prompt=¿Ya dispone de una cuenta? {0}
login.login.prompt.text=Iniciar sesión
login.moreOptions=Más opciones
login.alreadyLoggedIn=Usted ya ha iniciado sesión como {0}
login.loginWith=Iniciar sesión con {0}
login.signupWith=Registrarse con {0}
login.signup.submit=Registrarse
login.openid.submit=Registrarse con OpenID
login.openid=OpenID
login.email=Correo electrónico
login.email.change=Cambiar correo electrónico
login.email.new=Nueva dirección de correo electrónico
login.email.passwordConfirmation=Por motivos de seguridad, por favor, introduzca su contraseña actual debajo:
login.email.change.link=Haga click aquí para cambiar su dirección de correo electrónico
login.email.change.submit=Enviar
login.email.change.confirmation=Dirección de correo electrónico actualizada correctamente
login.email.emailIsOAuth=Las direcciones de correo electrónico asociados con un proveedor de autentiación (como Google o Facebook) no pueden ser cambiadas actualmente.
login.password=Contraseña
login.password.confirm=Confirmar contraseña
login.password.noPassword=Actualmente no hay ninguna contraseña asociada a su cuenta. Esto significa que usted se ha registrado a través de un proveedor externo de autenticación como Google o Facebook.
login.password.change=Cambiar contraseña
login.password.change.link=Haga click aquí para cambiar su contraseña
login.password.change.submit=Enviar
login.password.change.confirmation=Contraseña modificada correctamente
login.password.reset=Restablecer contraseña
login.password.forgot=¿Ha olvidado su contraseña?
login.password.reset.text=¿Ha olvidado su contraseña? Ingrese la dirección de correo electrónico que uso para el registro debajo y le enviaremos un enlace para que pueda restablecerla.
login.password.reset.noPasswordMatching=No hay ninguna cuenta que coincida {0}
login.password.reset.submit=Enviar enlace para restablecer la contraseña
login.openid.urlPlaceholder=Ingrese su URL de OpenID
login.disabled=El inicio de sesión esta desactivado actualmente. Por favor, inténtelo de nuevo más tarde.
login.oauth.info=Si usted ya tiene una cuenta en uno de estos sitios puede hacer click en el logo correspondiente para iniciar sesión en EHRI:
login.oauth.google=Google
login.oauth.facebook=Facebook
login.oauth.yahoo=Yahoo
login.oauth.microsoft=Microsoft
login.oauth.openid=OpenID
login.oauth.orcid=ORCID
login.oauth.linkedin=LinkedIn
login.password.info=Si usted ya ha creado una cuenta de EHRI previamente, ingrese su email y contraseña aquí:
login.password.submit=Registrarse
login.recover.link=Haga click aquí si ha olvidad o perdido su contraseña.
login.error=Hubo un problema con sus detalles
login.error.badUsernameOrPassword=Usuario o contraseña incorrectos
login.error.passwordsDoNotMatch=La contraseña y la confirmación de la contraseña no coinciden
login.error.badResetToken=El enlace usado para el restablecimiento de la contraseña no válido o ha expirado. Nota: los enlace para restablecer la contraseña sólo se pueden usar una vez.
login.error.oauth2.info=Le pedimos disculpas pero ha habido un error al intentar crear su cuenta usando {0}. Por favor, intente otro método de registro.
login.error.oauth2.orcid.missingEmail=No hemos sido capaces de obtener su dirección de correo electrónico desde ORCID. Por favor, asegúrese que su dirección de correo electrónico está establecida como pública o limitada en su perfi ORCID.
login.error.oauth2.orcid.missingGuid=No hemos sido capaces de obtener identificador ORCID. Por favor, asegúrese que su perfil ORCID está establecido como público o limitado.
login.error.oauth2.badSessionId=Esta vez no ha sido posible iniciar la sesión usando {0}. Por favor inténtelo de nuevo y si el error persiste, intente usar una ventana diferente en su navegador.
login.error.badEmail=La dirección de correo electrónico no se puede cambiar porque es inválida o ya está en uso.

login.password.current=Contraseña actual
login.password.new=Nueva contraseña
login.password.new.confirm=Confirme la nueva contraseña
login.password.reset.sentLink=Si su dirección de correo electrónico existe en el sistema recibirá un mensaje con un enlace que puede usar para restablecer su contraseña. Este enlace sólo puede ser usado una vez.
login.password.reset.confirmation=Su contraseña ha sido restablecida.
login.dataPolicyNag=Al registrarme, reconozco la {0} y acepto cumplir con los {1}.
logout=Cerrar sesión
logout.confirmation=Su sesión se ha cerrado
account=Cuenta

signup=Crear una cuenta
signup.dataPolicyNag=Al crear una cuenta yo declaro estar de acuerdo con los {0} y la {1}.
signup.badPasswords=La contraseña y la confirmación de contraseña no coincidedn. Por favor, inténtelo de nuevo.
signup.agreeTerms=Tiene que declarar su acuerdo con los términos.
signup.confirmation=¡Gracias por registrase! Le hemos enviado un correo electrónico con el cual puede verificar su dirección de correo electrónico.
signup.validation=Validar su cuenta
signup.validation.badToken=El enlace de confirmación es inválido, ya ha sido usado, o ha caducado. Nota: los enlaces de confirmación solo se pueden usar una vez.
signup.validation.confirmation=Su cuenta ha sido verificada..
signup.disabled=El registro en este sitio esta deshabilitado. Si está usando una instancia de pruebas, registrese en la instancia de producción y espere a que la sincronización de su cuenta se lleve a cabo for periodic account synchronisation to occur.
signup.validation.message=Haga click en el botón inferior para confirmar su dirección de correo electrónico.
signup.validation.message.submit=Confirmar dirección de correo electrónico
signup.validation.alreadyValidated=Su dirección de correo electrónico ha sido verificada.

#
# Mail stuff
#
mail.forgotPasswordMailHeading=Restablecimiento de contraseña en EHRI
mail.forgotPasswordMessage=Para restablecer su contraseña, por favor, siga en enlace de abajo. Nota: este enlace sólo puede ser usado para restablecer su contraseña una vez.
mail.forgotPasswordFooter=Reciba un cordial saludo,\nel equipo del EHRI
mail.confirmEmailHeading=Por favor, confirme su dirección de correo electrónico
mail.confirmEmailMessage=Por favor, confirme su dirección de correo electrónico visitando la siguiente dirección:
mail.confirmEmailFooter=Este enlace será válido por una semana, después de la cual su cuenta será desactivada.
mail.unverifiedEmailWarning=Cuenta sin verificar
mail.unverifiedEmailWarningMessage=La dirección de correo electrónico asociada con su cuenta todavía no ha sido verificada.
mail.unverifiedEmailResend=Para reenviar el correo electrónico de verificación
mail.unverifiedEmailResendSubmit=haga click aquí
mail.unverifiedEmailSubmit=Enviar correo electrónico de verificación
mail.emailConfirmationResent=Le hemos enviado un correo electrónico con el cual puede verificar su dirección de correo electrónico.
mail.message.subject=EHRI: Mensaje de {0}: {1}
mail.message.copy.subject=EHRI: Su mensaje a {0}: {1}
mail.message.heading=Nuevo mensaje de {0}
mail.message.copy.heading=Copia de su mensaje a {0}
mail.message.replyFooter=Para responder, conteste directamente a este mensaje. Puede cambiar sus preferencias para no recibir más mensajes de otros usuarios

bookmark.tooltip=Guardar este elemento en sus favoritos.
bookmark.item=Marca elemento como favorito
bookmark.item.tooltip=Guardar elemento en sus favoritos.
bookmark.item.submit=Enviar

bookmarkSet.list=Listas de favoritos
bookmarkSet.defaultSetName=Elementos favoritos
bookmarkSet.create=Crear nueva lista de favoritos
bookmarkSet.create.submit=Enviar
bookmarkSet.edit=Editar su lista de favoritos
bookmarkSet.edit.submit=Enviar
bookmarkSet.delete=Eliminar su lista de favoritos
bookmarkSet.delete.submit=Eliminar
bookmarkSet.noBookmarkSetsYet=Todavía no ha creado ninguna lista de favoritos.
bookmarkSet.noUserBookmarkSetsYet={0} no tiene todavía ninguna lista pública de favoritos.

#
# Descriptions
#
description.additionalData=Datos adicionales importador (Hacer click para mostrar)
description.additionalData.key=Clave
description.additionalData.value=Valor
description.noDescriptions=Este elemento no cuenta con una descripción
description.multiple=Descripciones
description.multiple.text=Este elemento tiene mútiples descripciones. Las descripciones alternativas pueden proceder de otra fuente o representar una traducción parcial.


#
# Access points
#
accessPoint.browse=Buscar
accessPoint.type.creator=Creador/es
accessPoint.type.creator.reverse=Creador de
accessPoint.type.person=Personas
accessPoint.type.person.reverse=Referenciado por (como persona)
accessPoint.type.family=Familias
accessPoint.type.family.reverse=Referenciado por (como familia)
accessPoint.type.corporateBody=Instituciones
accessPoint.type.corporateBody.reverse=Referenciado por (como institución)
accessPoint.type.place=Lugares
accessPoint.type.place.reverse=Lugares
accessPoint.type.subject=Tema
accessPoint.type.subject.reverse=Tema de
accessPoint.type.genre=Género
accessPoint.type.genre.reverse=Género
accessPoint.type.other=Varios
accessPoint.type.other.reverse=Varios
# References not on this item, but from another ites access points
accessPoint.externalReferences=Referencias externas a este elemento

#
# System Events
#
systemEvent.itemAtTime=Acción el {0}
systemEvent.lastUpdated=Actualizado {0}
systemEvent.creation=Elemento creado
systemEvent.createDependent=Elemento modificado
systemEvent.modification=Elemento modificado
systemEvent.modifyDependent=Elemento modificado
systemEvent.deletion=Elemento eliminado
systemEvent.deleteDependent=Elemento modificado
systemEvent.link=Enlace creado
systemEvent.annotation=Anotación creada
systemEvent.setGlobalPermissions=Permisos globales modificados
systemEvent.setItemPermissions=Permisos a nivel de elemento modificados
systemEvent.setVisibility=Visibilidad modificada
systemEvent.addGroup=Miembro añaidod a un grupo
systemEvent.removeGroup=Miembro eliminado de un grupo
systemEvent.ingest=Elementos importados
systemEvent.ingest.subjects={0} y {1} otros elementos
systemEvent.promotion=Elemento promocionado
systemEvent.demotion=Eliminado elemento promocionado
systemEvent.watch=Comenzó a seguir un elemento
systemEvent.unwatch=Dejó de seguir un elemento
systemEvent.follow=Comenzó a seguir a un usuario
systemEvent.unfollow=Dejó de seguir a un usuario
systemEvent.from=Desde
systemEvent.to=Hasta


#
# Miscellaneous errors
#
error.globalErrors=Hubo problemas con el formulario:
error.date=La fecha es incorrrecta o no ha sido formateada correctamente
error.badUrl=La URL no es correcta o no ha sido formateada correctamente. Nota: para las direcciones web, una URL válida tiene indicar el schema como prefijo  p. ej., ''http://'' o ''https://''
error.required=Obligatorio
error.emailExists=La dirección de correo electrónico es inválida o ya ha sido registrada
error.unknownUser=Usuario desconocido
error.emailNotFound=Esa dirección de correo electrónico no ha sido encontrada en nuestro sistema
error.badRecaptcha=La verificación Recaptcha (destinada a asegurar que usted es un humano y no una máquina automática de SPAM) no obtuvo la respuesta adecuada. Consejo: puede actualizar el texto de verificación hasta que consiga un texto más inteligible.
error.rateLimit=Número máximo de intentos superado. Por favor, espere {0} minutos antes de volver a intentarlo.
error.openId=Hubo un problema iniciando su sesión con OpenID.Esto puede deberse a que su proveedor no soporta el intercambio de atributos.
error.openId.url=No hubo respuesta del proveedor de openid dado: {0}.Por favor, asegurese que la URL del proveedor es correcta.
error.userEmailAlreadyRegistered=Ya hay un usuario registrado con esa dirección de correo electrónico: {0}

#
# Search
#
search.submit=Buscar
search.site=Buscar en la página...
search.clear=Borrar búsqueda
search.sort=Ordenar
search.sort.id=ID/Código
search.sort.id.title=Mostrar resultados ordenados por el identificador atribuido por la institución, con los elementos contenedores primero
search.sort.name=Nombre
search.sort.name.title=Mostrar resultado ordenados por nombre
search.sort.updated=Actualizados recientemente
search.sort.updated.title=Mostrar los resultados actualizados recientemente primero
search.sort.score=Relevancia
search.sort.score.title=Mostrar los resultados ordenados por la relevancia en la búsqueda
search.sort.detail=Detalle
search.sort.detail.title=Mostrar los resultados más detallados primero
search.sort.holder=Institución
search.sort.holder.title=Ordenador resultados por institución
search.sort.country=País
search.sort.country.title=Ordenar resultado por país
search.identifier=Identificador
search.spellingSuggestion=Quizás quiso decir
search.queryPlaceholder=Buscar...
search.results.list={3,choice,0#{0}|1#{0} - Un elemento|1<{0} - {1,number,integer} hasta {2,number,integer} de {3,number,integer}}
search.item.results.list={3,choice,0#{0}|1#{0} - Un elemento|1<{0} - Elementos {1,number,integer} hasta {2,number,integer} de {3,number,integer}}

search.field.all=Todos los campos
search.field.identifier=Identificador
search.field.title=Título
search.field.creator=Creador
search.field.scopeAndContent=Alcance y contenido
search.field.person=Personas
search.field.subject=Temas
search.field.place=Lugares
search.facets.heading=Filtrar resultados
search.facets.remove=Borrar filtros
search.facets.removeAll=Borrar todos los filtros

#
# Facets - these are ways of filtering search results
# and their associated tool-tips
#
facet.type=Tipo de elemento
facet.type.tooltip=El tipo de elemento, p. ej., instituciones, descripciones archivísticas,o informes de país.
facet.lang=Lengua de la descripción
facet.lang.placeholder=Filtrar lengua
facet.lang.tooltip=La lengua de la descripción digital.
facet.parent=Estructura
facet.parent.tooltip=Si este elemento actua como contenedor de otros elementos.
facet.parent.true=Elemento contenedor
facet.parent.false=Elemento único
facet.source=Fuente de la descripción
facet.source.tooltip=La fuente de la descripción digital, p. ej., si ha sido creada por el personal del EHRI o importada a partir de los datos estructurados cedidos por una institución asociada.
facet.source.MANUAL=EHRI
facet.source.IMPORT=Institución asociada al EHRI
facet.lod=Nivel de detalle
facet.lod.tooltip=Una approximation del nivel de detalle de una descripción basado en la suma de caracteres.
facet.lod.low=Bajo
facet.lod.medium=Medo
facet.lod.high=Alto
facet.lod.placeholder=Seleccionar nivel de detalle
facet.kw=Palabras clave
facet.kw.tooltip=Palabras clave asociadas con cada descripción.
facet.kw.placeholder=Filtrar palabras clave
facet.manifestation=Manifestación
facet.manifestation.tooltip=Elementos descritos según su organización física, o elementos ''virtuales'' agregando descripciones de documentación de diferentes fuentes que potencialmente pueden esta custodiada por diferentes instituciones.
facet.manifestation.DocumentaryUnit=Real/Física
facet.manifestation.VirtualUnit=Colección virtual
facet.country=País
facet.country.tooltip=El país en el que la documentación se encuentra custodiada.
facet.country.placeholder=Filtrar país
facet.location=Ubicación
facet.holder=Institución
facet.holder.tooltip=La institución donde la documentación se encuentra custodiada.
facet.holder.placeholder=Filtrar por institución
facet.container=Estructura del elemento
facet.topLevel.true=Nivel más alto
facet.container.hasChildItems=Múltiples niveles (contenedor)
facet.itemsHeldOnline=Elementos visibles
facet.itemsHeldOnline.yes=Documentación catalogada disponible
facet.cpf=Tipo de registro de autoridad
facet.cpf.tooltip=El tipo de entidad. p. ej., persona, familiar o institución.
facet.set=Registro de autoridad
facet.set.tooltip=El registro de autoridad al que el elemento pertenece.
facet.scope=Alcance
facet.scope.tooltip=El alcance del documento respecto al mandato del EHRI.
facet.priority=Prioridad
facet.priority.tooltip=La prioridad de una institución respecto del accesso a los datos del EHRI.
facet.active=Actividad
facet.active.tooltip=Usuarios activos o inactivos.
facet.staff=Estado como personal
facet.staff.tooltip=Solo usarios que son personal del EHRI.
facet.group=Grupos
facet.group.tooltip=Grupos a los que pertenece el usuario.
facet.top=Nivel más alto
facet.top.tooltip=Mostrar solamente los elementos del nivel superior (más generico).
facet.vocab=Vocabulario
facet.vocab.tooltip=El vocabulario o lista al que la palabra clave pertenece.
facet.promotable=Promocionable
facet.promotable.tooltip=Elementos que pueden ser promocionados o degradados.
facet.promoted=Promocionado
facet.promoted.tooltip=Elementos que están actualmente promocionados o degradados.
facet.score=Puntuación de promoción
facet.score.tooltip=La suma total de todas las promociones o degradaciones, si existe alguna.
facet.data=Disponibilidad de datos
facet.data.tooltip=Mostrar solamente las instituciones para las cuales el EHRI tenga descripciones.
facet.data.yes=Descripciones archivísticas
facet.restricted=Visibilidad
facet.restricted.tooltip=Si un elemento is accesible o no sólo para ciertos usuarios o grupos.
facet.restricted.true=Restringido
facet.restricted.false=No restringido
facet.dates=Fechas
facet.dates.tooltip=Una fecha o rango de fechas abarcadas por la documentación, si dichas fechas se han provisto en la documentación.



#
# Activity: https://portal.ehri-project.eu/activity
#
activity=Actividad
activity.by={0} - Actividad
activity.latestActivity=Última actividad
activity.userCreatedItem={2,choice,0#{0} elementos creados {1}|1#{0} creado {1} y un elementos adicional|1<{0} creado {1} y {2,number,integer} elementos adicionales}
activity.userUpdatedItem={2,choice,0#{0} elementos actualizados {1}|1#{0} actualizados {1} y un elemento adicional|1<{0} actualizados {1} y {2,number,integer} elementos adicional}
activity.userImportedItem={2,choice,0#{0} elementos importados {1}|1#{0} importado {1} y un elemento adicional|1<{0} importado {1} y {2,number,integer} elementos adicionales}
activity.userLinkedItem={2,choice,0#{0} enlace creado para un elemento {1}|1#{0} enlace creado para {1} y un elemento adicional|1<{0} enlace creado para {1} y {2,number,integer} elementos adicionales}
activity.userAnnotatedItem={2,choice,0#{0} elemento anotado {1}|1#{0} anotado {1} y un elemento adicional|1<{0} anotado {1} y {2,number,integer} elementos adicionales}
activity.fetchMore=Más
activity.noActivityYet=La actividad sobre los elementos y usuarios que usted está siguiendo aparecerá aquí.
activity.noUserActivityYet={0} no tiene actividad pública por el momento.
activity.modifiedProfile=Información del perfil actualizada.

#
# 'Social' stuff...
#
social.message.send=Contactar {0}
social.message.send.submit=Enviar mensaje
social.message.send.confirmation=Su mensaje ha sido enviado
social.message.send.subject=Asunto
social.message.send.message=Su mensaje
social.message.send.copy=Recibir una copia del mensaje
social.message.send.warning=Su mensaje ha sido enviado con su dirección de correo electrónico en el campo responder a.
social.message.send.userNotAcceptingMessages=El usuario tiene la recepción de mensaje deshabilitada.
social.users=Personas
social.browseUsers=Personas en el EHRI
social.follow=Seguir
social.follow.submit=Seguir usuario
social.unfollow=Dejar de seguir
social.unfollow.submit=Dejar de seguir usuario
social.followers=Seguidores
social.following=Siguiendo
social.followedBy=Seguido por
social.noFollowersYet=Nadie le sigue por el momento.
social.noFollowingYet={0} no está siguiendo a nadie por el momento.
social.noItemsWatchedYet=No está siguiendo ningún elemento todavía.
social.noItemsWatchedYet.detail=Haciendo click en el boton "Seguir elemento""en las descripciones de los elementos hará que los actualizaciones de dichos elementos aperezcan en su hilo de actividad.
social.noUserItemsWatchedYet={0} no está siguiendo ningún elemento por el momento.
social.userFollowing=Usuarios seguidos {0}
social.usersFollowedBy=Usuarios seguidos por {0}
social.block=Bloquear usuario
social.block.submit=Bloquear usuario
social.unblock=Desbloquear usuario
social.unblock.submit=Desbloquear usuario


#
# Annotations
#
annotation=Notas
annotation.label=Nota de ''{0}'' a las {1}
annotation.description=Notas en esta descripción
annotation.search=Buscar notas...
annotation.search.noneFound=No se han encontrado notas que coincidan con ''{0}''.
annotation.field=Campo
annotation.list=Notas
annotation.list.by={0} - Notas
annotation.none=Usted no ha escrito ninguna nota todavía.
annotation.none.detail=Puede escribir notas (publicas or privadas) en los elementos haciendo click en el botón "Añadir nota" en las descripciones o en los campos individuales.
annotation.create=Añadir nota
annotation.create.submit=Añadir nota
annotation.create.title=Crear una nota vinculada a esta descripción
annotation.field.create=Añadir nota
annotation.field.create.submit=Añadir nota
annotation.field.create.title=Crear una nota en este campo
annotation.update=Editar
annotation.update.submit=Guardar
annotation.update.title=Editar nota
annotation.delete=Eliminar
annotation.delete.submit=Eliminar
annotation.delete.title=Eliminar nota
annotation.field.showHidden=Mostrar/esconder {0,choice,1#1 nota pública escrita por otra persona|1<{0} notas públicas escritas por otras personas}
annotation.createdBy=Escrita por {0} {1}
annotation.showPublic={0,choice,1#Motras una nota pública|1<Mostrar {0} notas públicas}
annotation.placeholder=

#
# Visibility
#
access.all=Público
access.accessibleTo=Visible para {0}
access.accessibleTo.extended={0,choice,0#|1# y otro|1< y otros {0}}

#
# Promotion
#
promotion.isPromotable=Promoción
promotion.isPromotable.true=Promocionable
promotion.isPromotable.false=No promocionable
promotion.isPromoted.true=Promocionado
promotion.isPromoted.false=Sin promocionar
promotion.promotedBy=Promocionado por {0}
promotion.promotedBy.extended={0,choice,0#|1# y otro|1< y otros {0}}
promotion.promote=Promocionar
promotion.promote.title=Promocionar nota
promotion.promote.title.submit=Promocionar
promotion.promote.remove=Eliminar promoción
promotion.promote.remove.title=Eliminar nota de promoción
promotion.promote.remove.title.submit=Eliminar
promotion.demote=Degradar
promotion.demote.title=Degradar nota
promotion.demote.title.submit=Degradar
promotion.demote.remove=Eliminar degradación
promotion.demote.remove.title=Eliminar nota de degradación
promotion.demote.remove.title.submit=Eliminar
promotion.score=Puntuación de promoción
promotion.score.detail=Promociones: {0}, Degradaciones: {1}
promotion.score.negative=Negativo
promotion.score.neutral=Neutral
promotion.score.positive=Positivo

#
# Activity time line
#
timeline.eventType.ingest={0} importó {1}
timeline.eventType.creation={0} creó {1}
timeline.eventType.annotation={0} anotó {1}
timeline.eventType.modification={0} actualizó {1}
timeline.eventType.link={0} enlazó {1}
timeline.eventType.createDependent={0} actualizó {1}
timeline.eventType.modifyDependent={0} actualizó {1}
timeline.eventType.deleteDependent={0} actualizó {1}
timeline.eventType.watch={0} empezó a seguir {1}
timeline.eventType.follow={0} empezó a seguir {1}
timeline.eventType.setVisibility={0} actualizó {1}
timeline.eventType.setGlobalPermissions={0} actualizó {1}
timeline.eventType.setItemPermissions={0} actualizó {1}
timeline.eventType.promotion={0} promocionó {1}
timeline.eventType.demotion={0} degradó {1}
timeline.target.Group=un grupo
timeline.target.UserProfile=un usuario
timeline.target.Repository=un archivo
timeline.target.HistoricalAgent=un registro de autoridad
timeline.target.DocumentaryUnit=una descripción archivística
timeline.target.CvocVocabulary=un vocabulario
timeline.target.AuthoritativeSet=una lista de registros de autoridad
timeline.target.CvocConcept=un término
timeline.target.Annotation=una anotación
timeline.target.Country=un país
timeline.target.Link=un enlace
timeline.target.SystemEvent=un historial

#
# Visibility of user-generated content
#
contribution.visibility=Escoger quien puede ver esto
contribution.visibility.me=Sólo yo
contribution.visibility.groups=Mis grupos
contribution.visibility.custom=Personalizado
contribution.visibility.allowPublic=Permitir promociones
contribution.visibility.allowPublic.title=Permitir que los moderadores del EHRI puedan promocionar esta nota de manera que sea visible para todos los usuarios.
contribution.visibility.isPrivate=Nota privada
contribution.visibility.isPublic=Nota pública
contribution.visibility.isPublic.title=Deshabilitar esta opción para hacer esta nota privada. Por defecto, los moderadores del EHRI pueden promocionar las notas para que sean visibles para todo los usuarios.

#
# General search
#
search=Buscar
search.sort.title=Ordenar resultados de búsqueda
search.global=Buscar archivos, descripciones e informes de país...
search.Repository=Buscar archivos...
search.DocumentaryUnit=Buscar descripciones archivísticas...
search.HistoricalAgent=Buscar registro de autoridad...
search.VirtualUnit=Buscar colecciones virtuales...
search.Country=Buscar informes de país...
search.UserProfile=Buscar personas...
search.CvocConcept=Buscar palabras clave...
search.CvocVocabulary=Buscar vocabularios...
search.AuthoritativeSet=Buscar lista de registros de autoridad...
search.helper.example=Término de búsqueda
search.helper.linkTitle=Consejos para la búsqueda avanzada
search.helper=Hay varias formar de hacer búsquedas más específicas:
search.helper.field=Use la siguiente sintaxis para restringir campos específicos:
search.helper.field.example=campo:"término de búsqueda"
search.helper.field.example.description=Por ejemplo, puede buscar todas las descripciones que contengan el término de búsqueda "Auschwitz Birkenau" en su título usando title:"Auschwitz Birkenau".
search.helper.field.available=Los siguientes campos están actualmente disponibles:
search.helper.minus=Para excluir una palabra de la búsqueda, p. ej., -Gestapo
search.helper.plus=El término tiene que estar en el resultado p ej., +Gestapo
search.helper.quotes=Obtener un resultado exacto, p. ej., "Member lists"

#
# Feedback
#
feedback=Dejar un comentario
feedback.submit=Enviar
feedback.message=Háganos saber lo que piensa...
feedback.thanks=¡Gracias!
feedback.thanks.message=Sus comentarios son muy agradecidos.
feedback.list=Comentarios enviados
feedback.type=Este comentario es sobre
feedback.type.site=el sitio webthe site (funcionalidad, diseño, etc...)
feedback.type.data=el contenido/datos
feedback.delete=Eliminar
feedback.delete.submit=Eliminar elemento item
feedback.disabled=El envío de comentario está desactivado ahora mismo.

#
# Data policy at https://portal.ehri-project.eu/data-policy
#
dataPolicy=Política de datos
dataPolicy.header=Política de datos del EHRI
dataPolicy.modal.notice.1=Esta base de datos puede contener categorías especiales de datos personales, incluyendo datos de de personas relacionadas con condenas o delitos criminales que, de acuerdo con el artículo 9, sección 2, punto j de la GDPR y con los artículos 24 y 32, punto f de la UAVG (Dutch personal data protection act); pueden ser usados con fines de investigación sólo bajo la condición de que se puedan salvaguardar los derechos y libertades del sujeto al que los datos se refieren.
dataPolicy.modal.notice.2=Las categorías de datos personales en el EHRI son cualquier dato relacionado con una persona identificada o identificable que pueda ayudar a relacionarlo a su vez a: sus creencias religiosas o filosóficas, su origen racial o étnico, sus opiniones políticas, su pertencia a sindicatos u organizaciones de trabajadores, su datos médicos, su vida sexual o su orientación sexual.
dataPolicy.modal.terms=Yo declaro que mi utilización de las categorías de datos personales relacionados con condenas o delitos criminales será puramente con fines de investigación y que prometo usar estos datos de acuerdo a lo establecido en las leyes y regulaciones neerlandesas e internacionales. Yo me declaro consciente e informado/a de los posibles resultados punitivos a los que puede dar lugar el incumplimiento de las mencionadas leyes y/o regulaciones.
dataPolicy.modal.agree=Estoy de acuerdo

#
# Terms and Conditions
# NB: the full page is now an external document in Google Docs
#
termsAndConditions=Términos y condiciones

#
# Contact page
#
contact.header=Ponerse en contacto con el EHRI
contact.p1=El coordinador del proyecto EHRI es NIOD Institute for War, Holocaust and Genocide Studies. Puede escribir a NIOD usando la siguiente dirección postal:
contact.p2=También disponemos de otras formas mendiante las cuales puede obtener más ayuda o puede ponerse en contacto con nosotros:
contact.p2.1=para cuestiones relativas a las políticas de datos o privacidad, por favor póngase en contacto con {0}.
contact.p2.2=si tiene cualquier pregunta sobre la documentación o las políticas de acceso de cualquiera de los archivos que contribuyen en el portal del EHRI, por favor contacte al archivo en cuestión usando la información de contacto proporcionada en la descripción del archivo.
contact.p2.3=para todas las demás preguntas o si usted quiere dejar un comentario, un mensaje de agradecimiento o una queja sobre el portal del EHRI, envíenos un correo electrónico a {0}.

#
# About page
#
about.heading=Sobre el EHRI
about.p1=El portal en línea del EHRI proporciona infromación sobre documentación de archivo relativa al Holocausto custodiada en instituciones dentro y fuera de Europa. El portal del EHRI puede ser accedido por cualquier de manera gratuita.
about.p2=El portal del EHRI busca superar uno de los retos más distintivos de la investigación sobre el Holocausto: la fragmentación y la amplia dispersión geográfica de la documentación sobre este evento. Integrando y contectando infromación sobre decenas de miles de fuentes de archivo custodiadas físicamente en miles de instituciones, el portal del EHRI es un recurso inestimable para cualquier persona interesada en el Holocausto, y posibilita nuevos métodos transnacionales y comparativos de investigación.
about.p3=El portal del EHRI es uno de los principales resultados del proyecto European Holocaust Research Infrastructure (EHRI), un consorcio que reune investigadores del Holocausto, archiveros y humanistas digitales de más de 20 instituciones. El portal del EHRI es un recurso en expansión donde nueva información y funcionalidades son continuamente añadidas.
about.p4=El EHRI es a la vez una infraestructura y una red humana. Además de la creación del portal, el proyecto EHRI ha desarrollado también {0} de uso libre, y ofrece un variado programa de actividades y enventos, incluyendo un programa de becas, escuelas de verano, talleres de expertos y conferencias. Puede encontrar más información sobre la historia del proyecto, las instituciones participantes y las actividades en {1}.
about.p4.a1=Curso en línea sobre estudios del Holocausto
about.p4.a2=sitio web del proyecto
about.p5=El poryecto EHRI ha recibido financiación del Seventh Framework Programme de la Unión Europea con número de acuerdo de subvención 261873 y del programa de investigaación e innovación Horizon 2020 de la Unión Europea con número de acuerdo de subvención 654164.


#
# Externally-hosted documents
#
pages.external.faq.title=Preguntas frecuentes
pages.external.faq.description=Preguntas frequentes sobre el portal del EHRI.
pages.external.notFound=Le pedimos disculpas pero ha habido un problema al cargar los datos de esa página.

# Markdown Cheatsheet
#
markdown.cheatsheet.title=Consejos de formateo de texto
markdown.cheatsheet.asterisks=asteriscos
markdown.cheatsheet.underscores=guiones bajos
markdown.cheatsheet.bold=Negrita
markdown.cheatsheet.italic=Cursiva
markdown.cheatsheet.ordered=Lista ordenada
markdown.cheatsheet.ordered.item1=Primer elemento de la lista ordenada
markdown.cheatsheet.ordered.item2=Otro elemento
markdown.cheatsheet.unordered=Lista no ordenada
markdown.cheatsheet.unordered.item1=Elemento
markdown.cheatsheet.unordered.item2=Otro elemento
markdown.cheatsheet.more=Fuente


#
# Item-type specific fields
#
# Generic
#
item.details=Detalles del elemento
item.history=Historial del elemento
item.history.more=Mostrar el historial completo
item.related=Elementos relacionados
item.related.search=Buscar elementos relacionados...
item.linked=Otros elementos conectados
item.copies=Copias
item.showChildItems=Descendientes (elementos)
item.showItems={0,choice,0#{0} Elementos|1#Un elemento|1<{0,number,integer} elementos}
item.adminPage=Módulo de administración
item.publicPage=Volver al módulo público

#
# Repositories
#
repository.address=Dirección
repository.contact=Contacto
repository.phone=Teléfono
repository.email=Correo electrónico
repository.fax=Fax
repository.url=Sitio web
repository.contactPerson=Persona de contacto
repository.street=Calle
repository.municipality=Ciudad
repository.firstdem=Región
repository.countryCode=Código de país
repository.telephone=Teléfono
repository.postalCode=Código postal
repository.webpage=Sitio web
repository.searchItems=Buscar los elementos custodiados por {0}...
repository.childItems=Descripciones archivísticas
repository.childItems.search={0,choice,0#{0} elementos |1#un elemento|1<{0,number,integer} elementos}
repository.search=Buscar archivos
repository.searchInside=Buscar elementos custodiados por {0}
repository.itemsHeldOnline=Elementos en el EHRI
repository.itemsHeldOnline.true=Sí
repository.itemsHeldOnline.false=No
repository.childCount={0,choice,0#No hay descripciones archivísticas disponibles|1#una descripción archivística disponible|1<{0,number,integer} descripciones archivísticas disponibles}

repository.identifier=Identificador
repository.name=Forma(s) autorizada(s) del nombre
repository.parallelFormsOfName=Forma(s) paralela(s) del nombre
repository.otherFormsOfName=Otra(s) forma(s) del nombre
repository.languageCode=Lengua de la descripción
repository.scriptCode=Lengua/escritura de los documentos
repository.institutionType=Tipo de institución
repository.history=Historia de la institución
repository.geoculturalContext=Contexto cultural y geográfico
repository.mandates=Atribuciones/Fuentes legales
repository.administrativeStructure=Estructura administrativa
repository.records=Gestión de documentos y política de ingresos
repository.buildings=Edificio(s)
repository.holdings=Fondos y otras colleciones custodiadas
repository.findingAids=Instrumentos de descripción, guías y publicaciones
repository.openingTimes=Horarios de apertura
repository.conditions=Condiciones y requisitos para el uso y el acceso
repository.accessibility=Accessibilidad
repository.researchServices=Servicios de ayuda a la investigación
repository.reproductionServices=Servicios de reproducción
repository.publicAreas=Espacios públicos
repository.descriptionIdentifier=Identificador de la descripción
repository.institutionIdentifier=Identificador de la institución
repository.rulesAndConventions=Reglas y/o convenciones
repository.status=Estado de la elaboración
repository.levelOfDetail=Nivel de detalle
repository.datesCVD=Fecha de creación, revisión o eliminación
repository.languages=Lengua(s) usadas
repository.scripts=Escritura(s) usadas
repository.sources=Fuentes
repository.maintenanceNotes=Notas de mantenimiento
repository.info.disclaimer=Si puede ayudarnos a mejorar esta información, por favor contactenos en {0}.

#
# Documentary units
#
documentaryUnit.identifier=Código(s) de referencia
documentaryUnit.ref=Página web
documentaryUnit.archivalContext=Contexto archivístico
documentaryUnit.otherIdentifiers=Código(s) alternativo(s)
documentaryUnit.extentAndMedium=Volumen y soporte de la unidad de descripción (cantida, tamaño o dimensiones)
documentaryUnit.scopeAndContent=Alcance y contenido
documentaryUnit.biographicalHistory=Historia institucional/Reseña biográfica
documentaryUnit.archivalHistory=Historia archivística
documentaryUnit.acquisition=Forma de ingreso
documentaryUnit.appraisal=Valoración, selección y eliminación
documentaryUnit.systemOfArrangement=Organización
documentaryUnit.archivistNote=Nota del archivero/a
documentaryUnit.rulesAndConventions=Reglas o normas
documentaryUnit.dates=Fecha(s)
documentaryUnit.levelOfDescription=Nivel de descripción
documentaryUnit.languageOfMaterials=Lengua(s) de la documentación
documentaryUnit.scriptOfMaterials=Escritura(s) de la documentación
documentaryUnit.searchItems=Buscar descendientes (elementos)...
documentaryUnit.levelOfDescription.fonds=Fondo
documentaryUnit.levelOfDescription.subfonds=Subfondo
documentaryUnit.levelOfDescription.collection=Colección
documentaryUnit.levelOfDescription.subcollection=Subcolección
documentaryUnit.levelOfDescription.recordgrp=Unidad documental compuesta
documentaryUnit.levelOfDescription.subgrp=Subunidad documental compuesta
documentaryUnit.levelOfDescription.series=Serie
documentaryUnit.levelOfDescription.subseries=Subserie
documentaryUnit.levelOfDescription.file=Expediente
documentaryUnit.levelOfDescription.item=Unidad documental simple
documentaryUnit.levelOfDescription.otherlevel=Otro
documentaryUnit.levelOfDescription.class=Clase
documentaryUnit.childItems=Descendientes (elementos)
documentaryUnit.childItems.search={0,choice,0#{0} nivel inferior |1#un elemento|1<{0,number,integer} elementos}
documentaryUnit.physicalLocation=Ubicación física
documentaryUnit.languageCode=Lengua de la descripción
documentaryUnit.abstract=Resumen
documentaryUnit.accruals=Nuevos ingresos
documentaryUnit.conditionsOfAccess=Condiciones de acceso
documentaryUnit.conditionsOfReproduction=Condiciones de reproducción
documentaryUnit.languageOfMaterial=Lengua(s) de la documentación
documentaryUnit.scriptOfMaterial=Escritura(s) de la documentación
documentaryUnit.physicalCharacteristics=Características físicas y requisitos técnicos
documentaryUnit.findingAids=Instrumentos de descripción
documentaryUnit.locationOfOriginals=Existencia y localización de los originales
documentaryUnit.locationOfCopies=Existencia y localización de copias
documentaryUnit.relatedUnitsOfDescription=Unidades de descripción relacionadas
documentaryUnit.separatedUnitsOfDescription=Unidadades de descripción separadas
documentaryUnit.publicationNote=Nota de publicaciones
documentaryUnit.notes=Nota(s)
documentaryUnit.sources=Fuentes
documentaryUnit.datesOfDescriptions=Fecha(s) de la descripción
documentaryUnit.sourceFileId=Identificador del fichero fuente
documentaryUnit.processInfo=Información del procesado
documentaryUnit.creationProcess=Fuente
documentaryUnit.creationProcess.MANUAL=EHRI
documentaryUnit.creationProcess.MANUAL.description=Esta descripción fue creada por el EHRI.
documentaryUnit.creationProcess.IMPORT=Socio del EHRI
documentaryUnit.creationProcess.IMPORT.disclaimer=Esta descripción ha sido elaborada directamente a partir de los datos estructurados cedidos al EHRI por una institución socia. Dicha institución considera esta descripción como un fiel reflejo de su documentation en el momento de la transferencia de los datos.

#
# Historical Agents
#
historicalAgent.identifier=Identificador
historicalAgent.dates=Fechas
historicalAgent.entityType=Tipo de entidad
historicalAgent.entityType.person=Persona
historicalAgent.entityType.family=Familia
historicalAgent.entityType.corporateBody=Institución
historicalAgent.set=Lista de registros de autoridad
historicalAgent.descriptionArea=Área de descripción
historicalAgent.identity=Área de identificación
historicalAgent.services=Área de servicios
historicalAgent.administrationArea=Área de control
mshistoricalAgent.creation=Creación
historicalAgent.name=Forma(s) autorizada(s) del nombre
historicalAgent.parallelFormsOfName=Formas paralelas del nombre
historicalAgent.standardisedFormsOfName=Formas normalizadas del nombre según otras reglas
historicalAgent.otherFormsOfName=Otras formas del nombre
historicalAgent.languageCode=Lengua(s) de la descripción
historicalAgent.typeOfEntity=Tipo de registro de autoridad
historicalAgent.corporateBody=Institución
historicalAgent.family=Familia
historicalAgent.person=Persona
historicalAgent.datesOfExistence=Fechas de existencia
historicalAgent.biographicalHistory=Historia
historicalAgent.place=Lugar(es)
historicalAgent.legalStatus=Estatus jurídico
historicalAgent.functions=Funciones, ocupaciones y actividades
historicalAgent.mandates=Atribucion(es)/Fuente(s) legal(es)
historicalAgent.structure=Estructura(s) interna(s)/Genealogía
historicalAgent.generalContext=Contexto general
historicalAgent.descriptionIdentifier=Identificador de la descripción
historicalAgent.institutionIdentifier=Identificador de la institución
historicalAgent.rulesAndConventions=Reglas y/o convenciones
historicalAgent.status=Estado de la elaboración
historicalAgent.levelOfDetail=Nivel de detalle
historicalAgent.datesCVD=Fechas de creación, revisión o eliminación
historicalAgent.languages=Lengua(s)
historicalAgent.scripts=Escritura(s)
historicalAgent.source=Fuentes
historicalAgent.maintenanceNotes=Notas de mantenimiento

#
# Concepts
#
cvocConcept.descriptions=Descripciones
cvocConcept.languageCode=Lengua
cvocConcept.identifier=Escritura (opcional)
cvocConcept.name=Etiqueta preferida
cvocConcept.altLabel=Etiqueta(s) alternativa(s)
cvocConcept.hiddenLabel=Etiqueta(s) oculta(s)
cvocConcept.note=Nota
cvocConcept.changeNote=Cambiar nota
cvocConcept.editorialNote=Nota editorial
cvocConcept.historyNote=Nota historica
cvocConcept.scopeNote=Nota sobre el alcance
cvocConcept.definition=Definición
cvocConcept.inVocabulary=Vocabulario
cvocConcept.search=Buscar términos
cvocConcept.broaderTerms=Términos generales
cvocConcept.narrowerTerms=Términos específicos
cvocConcept.narrowerTermsPageHeader={0,choice,0#No se han encontrado términos más espcíficos|1#Un término más específico encontrado|1<{0,number,integer} términos más específicos}
cvocConcept.longitude=Longitud
cvocConcept.latitude=Latitud
cvocConcept.url=URL
cvocConcept.uri=URI
cvocConcept.seeAlso=Ver también
cvocConcept.latLon=Ubicación geográfica (lat/lon)

cvocConcept.altLabel.description=
cvocConcept.hiddenLabel.description=
cvocConcept.scopeNote.description=
cvocConcept.longitude.description=Longitud geográfica.
cvocConcept.latitude.description=Latitud geográfica.
cvocConcept.uri.description=La Uniform Resource Identifier (URI).
cvocConcept.url.description=Una Uniform Resource Locator (URL) opcinal.
cvocConcept.seeAlso.description=Se usa para indicar que un recurso puede proveer información adicional sobre el sujeto del recurso.
cvocConcept.childCount={0,choice,0#No se han encontrado términos más específicos|1#Un término más específicos|1<{0,number,integer} términos más específicos}

#
# Virtual documentary units
#

virtualUnit.viewInArchivalContext=Ver en el contexto archivístico
virtualUnit.viewInArchivalContext.explanation=Este elemento se muestra como parte de una ''colección virtual'' que no refleja la organización física de la documentación en el archivo.

#
# Countries
#
# NB: This is a link to the EHRI Project site
country.report.intro=Una introducción general a los informes de país del EHRI se puede encontrar en la {0}.
country.report.toc=Índice
country.childItems=Archivos
country.childItems.search={0,choice,0#{0} archivos |1#un archivo|1<{0,number,integer} archivos}
country.search=Buscar países
country.searchItems=Buscar archivos...
country.childCount={0,choice,0#Ningún archivo disponible|1#Un archivo disponible|1<{0,number,integer} archivos disponibles}
country.items=Archivos
country.identifier=Código de país
country.identifier.description=El código ISO-3166-1 de 2 letra para este país
country.abstract=Resumen
country.report=Historia
country.situation=Situación de los archivos
country.dataSummary=Investigación del EHRI (Resumen)
country.dataExtensive=Investigación del EHRI (Ampliado)
country.list=Países
country.description.empty=Por el momento no hay ningún informe para este país.

#
# Vocabularies
#
cvocVocabulary.intro=Los vocabularios son colecciones de términos o lugares (ver también: {0}).
cvocVocabulary.childItems=Palabras clave
cvocVocabulary.searchItems=Buscar palabras clave en {0}...
cvocVocabulary.childCount={0,choice,0#Sin palabras clave|1#Una palabra clave|1<{0,number,integer} palabras clave}
cvocVocabulary.childItems.search={0,choice,0#Sin palabras clave|1#Una palabra clave|1<{0,number,integer} palabras clave}

#
# Authoritative Sets
#
authoritativeSet.intro=Los conjuntos de registros de autoridad son colecciones de personas, familias o institutiones (ver también: {0}).
authoritativeSet.childItems=Registros de autoridad
authoritativeSet.searchItems=Buscar registros de autoridad en {0}...
authoritativeSet.childCount={0,choice,0#Sin registros de autoridad|1#Un registro de autoridad|1<{0,number,integer} registros de autoridad}
authoritativeSet.childItems.search={0,choice,0#Sin registros de autoridad|1#1 registro de autoridad|1<{0,number,integer} registros de autoridad}

#
# Data Model
#
dataModel=Modelo de datos del EHRI
dataModel.legend=Leyenda
dataModel.reference=Consultar {0} para más información.
dataModel.description=El modelo de datos del EHRI es un modelo conceptual que describe la estructura de los datos albergados en el portal del EHRI. El modelo está basado en los estándares desarrollados por el Consejo Internacional de Archivos (CIA) para las descripciones archivísticas.
dataModel.field.name=Nombre del campo
dataModel.field.description=Descripción
dataModel.field.usage=Uso
dataModel.field.usage.mandatory=Obligatorio
dataModel.field.usage.mandatory.description=Este campo tiene que ser rellenado.
dataModel.field.usage.desirable=Recomendable
dataModel.field.usage.desirable.description=Este campo es recomendable pero no obligatorio.
dataModel.field.usage.optional=Opcional
dataModel.field.usage.optional.description=Este campo es opcional.
dataModel.field.seeAlso=Consultar también
dataModel.DocumentaryUnit.identityArea=Área de identificación
dataModel.DocumentaryUnit.contextArea=Área de contexto
dataModel.DocumentaryUnit.contentArea=Área de contenido y estructura
dataModel.DocumentaryUnit.conditionsArea=Área de condiciones de acceso y uso
dataModel.DocumentaryUnit.materialsArea=Área de materiales
dataModel.DocumentaryUnit.notesArea=Área de notas
dataModel.DocumentaryUnit.controlArea=Área de control de la descripción
dataModel.DocumentaryUnit.administrationArea=Área de administración
dataModel.Repository.identityArea=Área de identificación
dataModel.Repository.addressArea=Área de dirección
dataModel.Repository.descriptionArea=Área de descripción
dataModel.Repository.accessArea=Área de acceso
dataModel.Repository.servicesArea=Área de servicios
dataModel.Repository.controlArea=Área de control de la descripción
dataModel.HistoricalAgent.identityArea=Área de identificación
dataModel.HistoricalAgent.descriptionArea=Área de descripción
dataModel.HistoricalAgent.controlArea=Área de control de la descripción
