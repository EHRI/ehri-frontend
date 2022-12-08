import {mount} from '@vue/test-utils';
import App from './app.vue';
import AccessPoint from './components/_access-point';
import AccessPointEditorApi from "./__mocks__/api";
import {ConfigType} from "./types";

jest.mock('./api');

describe('App', () => {
  // Inspect the raw component options
  it('has data', () => {
    expect(typeof App).toBe('object')
  })
});

describe('Mounted App', () => {
  const wrapper = mount(App);

  test('is a Vue instance', () => {
    expect(wrapper.isVisible()).toBeTruthy()
    expect(wrapper.find("#access-point-editor").exists()).toBe(true);
    expect(wrapper.html()).toContain("Test Subject")
  });
});

test('opening the AP form', async () => {
  const wrapper = mount(App);

  // Need to to load the API data...
  await wrapper.isVisible();

  expect(wrapper.find(".ap-editor-new-access-point").exists()).toBe(false);
  expect(wrapper.find(".ap-editor-add-toggle").exists()).toBe(true);

  await wrapper.find(".ap-editor-add-toggle").trigger("click");

  expect(wrapper.find(".ap-editor-new-access-point").exists()).toBe(true);
});

test('deleting an AP', async () => {
  const config = {id: "a", did: "b"} as ConfigType;
  const wrapper = mount(AccessPoint, {
    props: {
      accessPoint: {
        id: "test-ap",
        isA: "AccessPoint",
        name: "Test"
      },
      api: new AccessPointEditorApi({}, config),
      config,
    }
  });

  await wrapper.isVisible();

  await wrapper.find(".ap-editor-remove-access-point").trigger("click");
  expect(wrapper.find(".remove-confirm button[data-apply='confirmation']").exists()).toBe(true);
  await wrapper.find(".remove-confirm button[data-apply='confirmation']").trigger("click");
  expect(wrapper.emitted()).toHaveProperty('deleted')
})
