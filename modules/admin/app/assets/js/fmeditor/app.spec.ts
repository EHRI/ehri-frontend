import {mount} from '@vue/test-utils';
import App from './app';
import ListEt from './components/_list-et';
import EntityTypeMetadataApi from "./api";

jest.mock('./api');

const defaultInit = {
  props: {
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
});

test('deleting a field', async () => {
  const wrapper = mount(ListEt, {
    props: {
      api: new EntityTypeMetadataApi({}),
    }
  });

  await wrapper.isVisible();
})
