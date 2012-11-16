<#ftl strip_whitespace=true>
<#--
  - File: metadata.ftl
  - 
  - Description: SP metadata
  - 
  - Required model data:
  -   resource
  -
  - XXX: cleanup URL config
  -->
<?xml version="1.0" encoding="UTF-8"?>
<EntityDescriptor
  xmlns="urn:oasis:names:tc:SAML:2.0:metadata"
  entityID="https://${entityId}"> 
  <SPSSODescriptor
    protocolSupportEnumeration="urn:oasis:names:tc:SAML:2.0:protocol">
    <SingleLogoutService
      Binding="urn:oasis:names:tc:SAML:2.0:bindings:HTTP-Redirect"     
      Location="https://${singleLogoutService}"/>
    <NameIDFormat>urn:oasis:names:tc:SAML:2.0:nameid-format:transient</NameIDFormat>
    <AssertionConsumerService index="0"
      Binding="urn:oasis:names:tc:SAML:2.0:bindings:HTTP-POST"    
      Location="https://${assertionConsumerService}"/>
  </SPSSODescriptor>
  <ContactPerson contactType="technical">
    <GivenName>${contactPersonGivenName}</GivenName> 
    <SurName>${contactPersonSurName}</SurName>
    <EmailAddress>${contactPersonEmailAddress}</EmailAddress>
  </ContactPerson>
</EntityDescriptor>
