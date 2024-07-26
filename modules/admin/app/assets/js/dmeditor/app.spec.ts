import {mount, flushPromises,config} from '@vue/test-utils';
import App from './app.vue';
import ListEt from './components/_list-et.vue';
import EntityTypeMetadataApi from "./api";

jest.mock('./api');

config.global.mocks = {
  '$t': (msg) => msg, // return i18n key
}

const defaultInit = {
  props: {
    service: {},
    config: {}
  }
}

describe('App', () => {
  // Inspect the raw component options
  it('has data', () => {
    expect(typeof App).toBe('object')
  })
});

describe('Mounted App', () => {
  const wrapper = mount(App, defaultInit);

  test('is a Vue instance', () => {
    expect(wrapper.isVisible()).toBeTruthy()
    expect(wrapper.find("#entity-type-metadata-editor").exists()).toBe(true);
    expect(wrapper.html()).toContain("Country")
  });
});

test('opening the ET form', async () => {
  const wrapper = mount(App, defaultInit);

  // Need to to load the API data...
  await wrapper.isVisible();

  expect(wrapper.find(".fm-editor").exists()).toBe(true);
  await flushPromises();
  expect(wrapper.find(".fm-list").exists()).toBe(true);
  await wrapper.find("#fm-editor-add-field-Country").trigger("click");
  expect(wrapper.find("#field-metadata-editor-form").exists()).toBe(true);
});

test('deleting a field', async () => {
  const wrapper = mount(ListEt, {
    props: {
      api: new EntityTypeMetadataApi({}, {}),
    }
  });

  await wrapper.isVisible();
  expect(wrapper.find(".fm-editor").exists()).toBe(true);
  await flushPromises();
  expect(wrapper.find(".fm-list").exists()).toBe(true);
  expect(wrapper.find("#fm-Country-history").exists()).toBe(true);

  await wrapper.find("#fm-Country-history .fm-edit").trigger("click");
  expect(wrapper.find("#delete-metadata").exists()).toBe(true);
  await wrapper.find("#delete-metadata").trigger("click");
  expect(wrapper.find(".confirm-delete-field-metadata").exists()).toBe(true);
  await wrapper.find(".confirm-delete-field-metadata button.accept").trigger("click");
  await flushPromises();
  expect(wrapper.find("#fm-Country-history").exists()).toBe(false);
})
